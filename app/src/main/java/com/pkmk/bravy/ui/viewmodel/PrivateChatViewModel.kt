package com.pkmk.bravy.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.remote.dto.ChatMessageDto
import com.pkmk.bravy.data.remote.dto.toMessage
import com.pkmk.bravy.data.remote.socket.ChatNotificationSync
import com.pkmk.bravy.data.remote.socket.SocketEvent
import com.pkmk.bravy.data.remote.socket.SocketManager
import com.pkmk.bravy.data.repository.AuthRepository
import com.pkmk.bravy.data.repository.ChatRepository
import com.pkmk.bravy.data.repository.applyIncomingMessage
import com.pkmk.bravy.data.repository.conversationIdOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Daftar percakapan privat beserta saran teman.
 *
 * Data percakapan berasal dari backend (`GET /chat`); pembaruan waktu nyata
 * datang dari [SocketManager] dalam dua bentuk: `chat:receive` yang membawa
 * pesan lengkap, dan `conversation:joined` yang menandai layar detail baru saja
 * membuka sebuah percakapan (biasanya karena ada pesan baru). Keduanya dipakai
 * untuk memperbarui daftar tanpa menunggu pengguna menarik layar.
 */
@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val socketManager: SocketManager,
    private val chatNotificationSync: ChatNotificationSync
) : ViewModel() {

    private val _recentChats = MutableLiveData<Result<List<RecentChat>>>()
    val recentChats: LiveData<Result<List<RecentChat>>> = _recentChats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _friends = MutableLiveData<Result<List<User>>>()
    val friends: LiveData<Result<List<User>>> = _friends

    private val _navigateToChat = MutableLiveData<Pair<String, User>?>()
    val navigateToChat: LiveData<Pair<String, User>?> = _navigateToChat

    /**
     * Id percakapan yang harus dibuka karena permintaan dari luar layar ini,
     * mis. notifikasi chat FCM. Bernilai sekali pakai: layar mengosongkannya
     * lewat [onOpenConversationHandled] setelah navigasi dijalankan, sehingga
     * tidak terpicu lagi saat layar kembali terlihat.
     */
    private val _openConversation = MutableLiveData<String?>()
    val openConversation: LiveData<String?> = _openConversation

    private val _isRefreshing = MutableLiveData<Boolean>()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var socketJob: Job? = null

    /** Langganan permintaan muat ulang dari pesan chat FCM. */
    private var syncJob: Job? = null

    /**
     * Menyambungkan soket dan berlangganan event chat. Dipanggil dari `onResume`;
     * langganan lama dilepas lebih dulu lewat [stopRealtimeUpdates] agar tidak
     * menumpuk, sedangkan [SocketManager.ensureConnected] aman dipanggil ulang.
     */
    fun startRealtimeUpdates() {
        socketManager.ensureConnected()
        observeNotificationSync()
        if (socketJob?.isActive == true) return

        socketJob = viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    // Pesan masuk hanya memperbarui entri yang bersangkutan, sehingga
                    // daftar tidak berkedip karena pemuatan ulang penuh.
                    is SocketEvent.MessageReceived -> {
                        val conversationId = event.message.conversationIdOrNull()
                        if (conversationId != null) {
                            updateLastMessage(conversationId, event.message)
                        }
                    }

                    // Bergabung ke sebuah percakapan menandakan layar detail baru
                    // dibuka, yang bisa berarti ada pesan baru atau status terbaca.
                    is SocketEvent.ConversationJoined -> loadRecentChats()

                    is SocketEvent.MessagesRead -> loadRecentChats()

                    else -> Unit
                }
            }
        }
    }

    /** Melepas langganan event saat layar tidak terlihat lagi. */
    fun stopRealtimeUpdates() {
        socketJob?.cancel()
        socketJob = null
        syncJob?.cancel()
        syncJob = null
    }

    /**
     * Menanggapi pesan chat yang datang lewat FCM saat aplikasi terlihat.
     *
     * Notifikasi untuk pesan seperti ini tidak ditampilkan, sehingga daftar
     * percakapan diperbarui dari sini: pesan yang membawa id percakapan yang
     * sudah dikenal disisipkan seperti event `chat:receive`, sedangkan percakapan
     * yang belum ada (baru dibuat dari perangkat lain) memicu pemuatan ulang.
     */
    private fun observeNotificationSync() {
        if (syncJob?.isActive == true) return

        syncJob = viewModelScope.launch {
            chatNotificationSync.refreshRequests.collect { conversationId ->
                val message = chatRepository.getMessages(conversationId).getOrNull()
                    ?.lastOrNull()
                val known = _recentChats.value?.getOrNull()
                    ?.any { it.chatId == conversationId } == true

                if (message != null && known) {
                    updateLastMessage(conversationId, message)
                } else {
                    loadRecentChats()
                }
            }
        }
    }

    /**
     * Membuka percakapan yang diminta dari luar layar ini, mis. setelah pengguna
     * menekan notifikasi chat. Profil lawan bicara diambil dari backend karena
     * pesan FCM hanya membawa id percakapan.
     */
    fun onOpenConversationRequested(conversationId: String) {
        viewModelScope.launch {
            chatRepository.getConversation(conversationId)
                .onSuccess { result ->
                    _navigateToChat.postValue(result.conversation.id to result.otherUser)
                }
                .onFailure { exception ->
                    Log.e(TAG, "Gagal membuka percakapan $conversationId", exception)
                    // Layar detail masih dapat dibuka dengan profil seadanya,
                    // sehingga pengguna tidak berakhir di layar kosong.
                    _navigateToChat.postValue(conversationId to User(uid = ""))
                }
        }
    }

    fun loadInitialData() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                loadFriends()
                loadRecentChats()
            } finally {
                kotlinx.coroutines.delay(2000) // Sedikit delay agar transisi mulus
                _isLoading.postValue(false)
            }
        }
    }

    fun refreshData() {
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                // Muat ulang semua data
                loadFriends()
                loadRecentChats()
            } finally {
                _isRefreshing.postValue(false)
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            // Sesi ditentukan oleh Bearer token, jadi uid tidak lagi dikirim.
            val result = authRepository.getFriendsBackend()
            result.onSuccess { friendInfoList ->
                // Filter hanya yang statusnya "friend" dan ambil data User-nya
                val friendUsers = friendInfoList
                    .filter { it.status == "friend" }
                    .map { it.user }
                _friends.postValue(Result.success(friendUsers))
            }.onFailure {
                _friends.postValue(Result.failure(it))
            }
        }
    }

    /**
     * Memuat semua percakapan terakhir untuk pengguna yang sedang login.
     *
     * Backend sudah mengurutkan percakapan dari yang paling baru, sehingga tidak
     * ada pengurutan ulang di sisi klien.
     */
    fun loadRecentChats() {
        viewModelScope.launch {
            _recentChats.value = chatRepository.getConversations()
        }
    }

    /**
     * Memperbarui pesan terakhir satu percakapan dari event `chat:receive`.
     *
     * Pesan teks tampil apa adanya, sedangkan pesan media dipetakan ke jenisnya
     * sehingga adapter menampilkan "Image"/"Voice Message" seperti biasa.
     */
    private fun updateLastMessage(conversationId: String, message: ChatMessageDto) {
        val current = _recentChats.value?.getOrNull() ?: return
        val updated = current.applyIncomingMessage(conversationId, message.toMessage())
        if (updated != current) {
            _recentChats.postValue(Result.success(updated))
        }
    }

    /**
     * Membuka (atau membuat) percakapan dengan [friend] lalu menavigasi ke layar
     * detail. Backend bersifat idempoten untuk pasangan peserta yang sama,
     * sehingga percakapan yang sudah ada tidak terduplikasi.
     */
    fun onFriendClicked(friend: User) {
        viewModelScope.launch {
            chatRepository.createConversation(friend.uid)
                .onSuccess { conversation ->
                    Log.d(TAG, "Percakapan siap: ${conversation.id}")
                    _navigateToChat.postValue(conversation.id to friend)
                }
                .onFailure { exception ->
                    Log.e(TAG, "Gagal memulai percakapan dengan ${friend.uid}", exception)
                    _error.postValue(exception.message ?: "Gagal memulai percakapan.")
                }
        }
    }

    fun onNavigationDone() {
        _navigateToChat.value = null
    }

    private companion object {
        const val TAG = "PrivateChatViewModel"
    }
}
