package com.pkmk.bravy.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.ChatItem
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.MissionType
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.remote.dto.ChatMessageDto
import com.pkmk.bravy.data.remote.dto.MEDIA_TYPE_TEXT
import com.pkmk.bravy.data.remote.dto.toMessage
import com.pkmk.bravy.data.remote.socket.SocketEvent
import com.pkmk.bravy.data.remote.socket.SocketManager
import com.pkmk.bravy.data.repository.AuthRepository
import com.pkmk.bravy.data.repository.ChatRepository
import com.pkmk.bravy.data.repository.conversationIdOrNull
import com.pkmk.bravy.data.repository.isFrom
import com.pkmk.bravy.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Layar percakapan privat.
 *
 * Seluruh isi percakapan berasal dari backend: riwayat lewat
 * `GET /chat/:id/messages`, pesan baru lewat event `chat:receive`, dan
 * pengiriman lewat `POST /chat/:id/messages`. Socket juga dipakai untuk
 * kehadiran (join/leave) serta indikator sedang menulis.
 *
 * Pesan yang dikirim langsung ditampilkan sebelum server menjawab (optimistis)
 * dengan `clientMessageId` sebagai penanda. Backend mengembalikan id yang sama
 * pada pesan yang tersimpan, sehingga pesan dari HTTP maupun dari socket dapat
 * dicocokkan dan digantikan tanpa muncul dua kali.
 */
@HiltViewModel
class DetailChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val socketManager: SocketManager
) : ViewModel() {

    private val _chatItems = MutableLiveData<List<ChatItem>>()
    val chatItems: LiveData<List<ChatItem>> = _chatItems

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _otherUser = MutableLiveData<User?>()
    val otherUser: LiveData<User?> = _otherUser

    private val _isUploading = MutableLiveData<Boolean>()
    val isUploading: LiveData<Boolean> = _isUploading

    /** Lawan bicara sedang menulis; dipakai untuk menampilkan indikator. */
    private val _isOtherUserTyping = MutableLiveData<Boolean>()
    val isOtherUserTyping: LiveData<Boolean> = _isOtherUserTyping

    /** Percakapan yang sedang dibuka, null saat layar belum/tidak aktif. */
    private var conversationId: String? = null
    private var currentUserId: String? = null

    /**
     * Sumber kebenaran pesan yang sedang ditampilkan. Disimpan terpisah dari
     * LiveData agar penggabungan pesan socket dan pesan optimistis tidak
     * bergantung pada nilai yang sudah dipublikasikan ke UI.
     */
    private val messagesByKey = LinkedHashMap<String, Message>()

    private var socketJob: Job? = null
    private var typingJob: Job? = null

    fun setOtherUser(user: User) {
        _otherUser.value = user
    }

    /** Profil lawan bicara dari backend; dipanggil saat id-nya belum diketahui. */
    fun loadOtherUser(userId: String) {
        viewModelScope.launch {
            chatRepository.getUserProfile(userId)?.let { _otherUser.postValue(it) }
        }
    }

    /**
     * Membuka percakapan: menyambungkan soket, bergabung ke room, memuat
     * riwayat, lalu berlangganan pesan masuk. Aman dipanggil dari `onResume`.
     */
    fun onConversationOpened(id: String) {
        if (conversationId == id && socketJob?.isActive == true) return
        conversationId = id

        socketManager.ensureConnected()
        socketManager.joinConversation(id)

        observeSocketEvents()

        viewModelScope.launch {
            currentUserId = chatRepository.getCurrentUserId()
            loadMessages(id)
        }
    }

    /** Keluar dari room dan melepas langganan event saat layar ditinggalkan. */
    fun onConversationClosed() {
        val id = conversationId ?: return
        // Status menulis diakhiri lebih dulu agar lawan bicara tidak melihat
        // indikator yang menggantung setelah layar ditutup.
        typingJob?.cancel()
        typingJob = null
        socketManager.emitTyping(id, isTyping = false)
        socketManager.leaveConversation(id)
        socketJob?.cancel()
        socketJob = null
    }

    private suspend fun loadMessages(id: String) {
        chatRepository.getMessages(id)
            .onSuccess { dtos ->
                messagesByKey.clear()
                dtos.forEach { dto -> merge(dto) }
                publish()
            }
            .onFailure { _error.postValue(it.message ?: "Gagal memuat pesan.") }
    }

    /**
     * Menyaring event soket untuk percakapan yang sedang dibuka. Event
     * percakapan lain diabaikan karena [SocketManager] dipakai bersama seluruh
     * layar chat.
     */
    private fun observeSocketEvents() {
        socketJob?.cancel()
        socketJob = viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    is SocketEvent.MessageReceived -> {
                        val message = event.message
                        if (message.conversationIdOrNull() == conversationId) {
                            merge(message)
                            publish()
                        }
                    }

                    is SocketEvent.TypingStarted -> {
                        if (event.event.conversationId == conversationId) {
                            _isOtherUserTyping.postValue(true)
                        }
                    }

                    is SocketEvent.TypingStopped -> {
                        if (event.event.conversationId == conversationId) {
                            _isOtherUserTyping.postValue(false)
                        }
                    }

                    is SocketEvent.ConversationError -> {
                        if (event.conversationId == conversationId) {
                            _error.postValue(event.message ?: "Tidak dapat membuka percakapan.")
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    // --- Pengiriman pesan ---

    fun sendMessage(
        conversationId: String,
        content: String,
        type: String,
        duration: Int = 0
    ) {
        send(conversationId, text = content, mediaType = type, mediaUrl = null)
    }

    /**
     * Mengirim gambar: berkas dikompres lalu diunggah ke Firebase Storage
     * (sesuai permintaan, media tetap memakai Storage), dan URL hasil unggahan
     * yang dikirim ke backend sebagai `mediaUrl`.
     */
    fun sendImageMessage(conversationId: String, imageUri: Uri, context: Context) {
        _isUploading.value = true
        viewModelScope.launch {
            try {
                val imageBytes = withContext(Dispatchers.IO) {
                    ImageUtils.compressImage(context, imageUri)
                }
                authRepository.uploadChatImage(imageBytes)
                    .onSuccess { imageUrl ->
                        send(
                            conversationId = conversationId,
                            text = null,
                            mediaType = MEDIA_TYPE_IMAGE,
                            mediaUrl = imageUrl
                        )
                    }
                    .onFailure { _error.postValue("Gagal mengunggah gambar: ${it.message}") }
            } catch (e: Exception) {
                _error.postValue("Terjadi kesalahan: ${e.message}")
            } finally {
                _isUploading.postValue(false)
            }
        }
    }

    /** Mengirim voice note; berkas diunggah ke Storage lebih dulu. */
    fun sendVoiceNote(conversationId: String, audioFile: File, durationInSeconds: Int) {
        _isUploading.value = true
        viewModelScope.launch {
            try {
                authRepository.uploadChatAudio(audioFile)
                    .onSuccess { audioUrl ->
                        send(
                            conversationId = conversationId,
                            text = null,
                            mediaType = MEDIA_TYPE_AUDIO,
                            mediaUrl = audioUrl,
                            duration = durationInSeconds
                        )
                    }
                    .onFailure { _error.postValue("Gagal mengunggah voice note: ${it.message}") }
            } catch (e: Exception) {
                _error.postValue("Terjadi kesalahan: ${e.message}")
            } finally {
                _isUploading.postValue(false)
            }
        }
    }

    /**
     * Mengirim pesan secara optimistis: gelembung pesan muncul lebih dulu dengan
     * [ChatRepository.newClientMessageId] sebagai penandanya, lalu digantikan
     * pesan asli dari server begitu tersimpan.
     */
    private fun send(
        conversationId: String,
        text: String?,
        mediaType: String,
        mediaUrl: String?,
        duration: Int = 0
    ) {
        val clientMessageId = chatRepository.newClientMessageId()

        messagesByKey[clientMessageId] = Message(
            messageId = clientMessageId,
            sender_uid = currentUserId.orEmpty(),
            type = mediaType,
            content = mediaUrl ?: text.orEmpty(),
            timestamp = System.currentTimeMillis(),
            duration = duration
        )
        publish()

        viewModelScope.launch {
            chatRepository.sendMessage(
                conversationId = conversationId,
                text = text,
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                clientMessageId = clientMessageId
            ).onSuccess { sent ->
                merge(sent)
                publish()
                // Misi harian hanya dihitung untuk pesan yang benar-benar terkirim.
                authRepository.completeDailyMission(MissionType.CHAT)
            }.onFailure { exception ->
                // Pesan optimistis dibuang agar tidak menyisakan gelembung yang
                // tidak pernah sampai ke server.
                messagesByKey.remove(clientMessageId)
                publish()
                _error.postValue(exception.message ?: "Pesan gagal dikirim.")
            }
        }
    }

    // --- Indikator sedang menulis ---

    /**
     * Menandai bahwa pengguna sedang menulis. Event `typing:start` hanya dikirim
     * sekali, lalu diikuti `typing:stop` setelah [TYPING_IDLE_TIMEOUT_MS] tanpa
     * ketikan berikutnya. Debounce di sisi klien penting karena backend hanya
     * meneruskan event apa adanya.
     */
    fun onMessageInputChanged(text: String) {
        val id = conversationId ?: return
        if (text.isNotBlank()) {
            if (typingJob?.isActive != true) {
                socketManager.emitTyping(id, isTyping = true)
            }
            typingJob?.cancel()
            typingJob = viewModelScope.launch {
                delay(TYPING_IDLE_TIMEOUT_MS)
                socketManager.emitTyping(id, isTyping = false)
            }
        } else {
            stopTyping()
        }
    }

    /** Menghentikan status menulis, mis. saat input dikosongkan atau dikirim. */
    fun stopTyping() {
        val id = conversationId ?: return
        if (typingJob?.isActive == true) {
            typingJob?.cancel()
            typingJob = null
            socketManager.emitTyping(id, isTyping = false)
        }
    }

    // --- Penggabungan pesan ---

    /**
     * Memasukkan pesan dari server ke daftar lokal.
     *
     * Pesan dengan `clientMessageId` yang sudah ada menggantikan versi
     * optimistisnya (kunci dilepas agar tidak tampil dua kali), sedangkan pesan
     * lain ditambahkan berdasarkan id-nya. Pesan tanpa id (mis. respons tak
     * terduga) diabaikan karena tidak dapat dicocokkan.
     */
    private fun merge(dto: ChatMessageDto) {
        val message = dto.toMessage()
        if (message.messageId.isBlank()) return

        val clientKey = dto.clientMessageId
        if (clientKey != null && clientKey != message.messageId) {
            messagesByKey.remove(clientKey)
        }
        messagesByKey[message.messageId] = message
    }

    /** Mengurutkan menurut waktu lalu menyisipkan pembatas tanggal. */
    private fun publish() {
        val sorted = messagesByKey.values.sortedBy { it.timestamp }
        _chatItems.value = createChatListWithSeparators(sorted)
    }

    private fun createChatListWithSeparators(messages: List<Message>): List<ChatItem> {
        val itemsWithSeparators = mutableListOf<ChatItem>()
        if (messages.isEmpty()) return itemsWithSeparators

        for (i in messages.indices) {
            val currentMessage = messages[i]
            val prevMessage = if (i > 0) messages[i - 1] else null

            val needsSeparator =
                prevMessage == null || !isSameDay(currentMessage.timestamp, prevMessage.timestamp)

            if (needsSeparator) {
                itemsWithSeparators.add(
                    ChatItem.DateSeparatorItem(formatDateSeparator(currentMessage.timestamp))
                )
            }
            itemsWithSeparators.add(ChatItem.MessageItem(currentMessage))
        }
        return itemsWithSeparators
    }

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun formatDateSeparator(timestamp: Long): String {
        val messageCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = Calendar.getInstance()

        // Cek apakah hari ini
        if (now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR)
        ) {
            return "Today"
        }

        // Cek apakah kemarin
        now.add(Calendar.DAY_OF_YEAR, -1)
        if (now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR)
        ) {
            return "Yesterday"
        }

        // Cek apakah dalam seminggu terakhir (sebelum kemarin)
        now.add(Calendar.DAY_OF_YEAR, 5) // Reset ke 6 hari yang lalu dari hari ini
        if (messageCalendar.after(now)) {
            return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        }

        // Jika lebih lama dari seminggu
        return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * Percakapan ditutup saat ViewModel dibuang (mis. pengguna menekan kembali).
     * `onPause` sudah menangani kasus normal, tetapi penutupan di sini menjamin
     * tidak ada langganan socket yang tertinggal.
     */
    override fun onCleared() {
        super.onCleared()
        onConversationClosed()
    }

    companion object {
        /** Media dikenali backend sebagai `image` / `audio` / `video` / `text`. */
        const val MEDIA_TYPE_IMAGE = "image"
        const val MEDIA_TYPE_AUDIO = "audio"

        /** Jeda tanpa ketikan sebelum `typing:stop` dikirim. */
        const val TYPING_IDLE_TIMEOUT_MS = 1_000L
    }
}
