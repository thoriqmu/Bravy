package com.pkmk.bravy.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import com.pkmk.bravy.data.source.FirebaseDataSource // Asumsi Anda memiliki data source ini
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val dataSource: FirebaseDataSource,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _recentChats = MutableLiveData<Result<List<RecentChat>>>()
    val recentChats: LiveData<Result<List<RecentChat>>> = _recentChats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _friends = MutableLiveData<Result<List<User>>>()
    val friends: LiveData<Result<List<User>>> = _friends

    private val _navigateToChat = MutableLiveData<Pair<String, User>?>()
    val navigateToChat: LiveData<Pair<String, User>?> = _navigateToChat

    fun loadInitialData() {
        _isLoading.value = true // Mulai loading
        viewModelScope.launch {
            try {
                // Panggil kedua fungsi load
                loadFriends()
                loadRecentChats()
            } finally {
                // Selesaikan loading setelah semua selesai
                kotlinx.coroutines.delay(5000) // Opsional
                _isLoading.postValue(false)
            }
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.getFriendsData(currentUid)
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

    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Memuat semua percakapan terakhir untuk pengguna yang sedang login.
     */
    fun loadRecentChats() {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _recentChats.value = Result.failure(Exception("User not logged in."))
                return@launch
            }

            try {
                // 1. Ambil semua ID chat milik pengguna
                val chatIds = dataSource.getUserChats(currentUser.uid)
                val chatList = mutableListOf<RecentChat>()

                // 2. Ulangi setiap chat untuk mendapatkan detailnya
                for (chatId in chatIds) {
                    // 3. Dapatkan UID lawan bicara
                    val participantUids = dataSource.getParticipantUids(chatId)
                    val otherUserUid = participantUids.firstOrNull { it != currentUser.uid }
                    if (otherUserUid != null) {
                        // 4. Dapatkan profil lawan bicara dan pesan terakhir
                        val otherUser = dataSource.getUser(otherUserUid)
                        val lastMessage = dataSource.getLastChatMessage(chatId)
                        if (otherUser != null) {
                            // 5. Gabungkan menjadi objek RecentChat
                            chatList.add(RecentChat(otherUser, chatId, lastMessage))
                        }
                    }
                }

                // 6. Urutkan daftar berdasarkan pesan paling baru
                chatList.sortByDescending { it.lastMessage?.timestamp }
                _recentChats.value = Result.success(chatList)

            } catch (e: Exception) {
                _recentChats.value = Result.failure(e)
            }
        }
    }

    fun onFriendClicked(friend: User) {
        viewModelScope.launch {
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                Log.e("PrivateChatViewModel", "Current user UID is null, cannot start chat.")
                // Mungkin tampilkan pesan ke user di sini jika perlu
                return@launch
            }

            val currentUserResult = authRepository.getUser(currentUserId)
            currentUserResult.onSuccess { currentUser ->
                // Pastikan currentUser.uid dan friend.uid tidak kosong/null sebelum membuat chatId
                if (currentUser.uid.isBlank() || friend.uid.isBlank()) {
                    Log.e("PrivateChatViewModel", "UID for currentUser or friend is blank. CurrentUserUID: ${currentUser.uid}, FriendUID: ${friend.uid}")
                    // Tampilkan pesan error ke pengguna
                    return@onSuccess
                }

                val chatId = generateChatId(currentUser.uid, friend.uid)
                val createResult = authRepository.createChatRoomIfNeeded(chatId, currentUser, friend)

                createResult.onSuccess {
                    // Jika sukses, trigger navigasi
                    Log.d("PrivateChatViewModel", "Chat room created/verified for chatId: $chatId. Navigating...")
                    _navigateToChat.postValue(chatId to friend)
                }.onFailure { exception ->
                    // Handle error jika gagal membuat chat room
                    Log.e("PrivateChatViewModel", "Failed to create or find chat room for chatId: $chatId", exception)
                    // TODO: Tampilkan pesan error kepada pengguna, misalnya menggunakan LiveData event untuk Toast
                    // _errorEvent.postValue("Gagal memulai chat. Coba lagi nanti.")
                }
            }.onFailure { exception ->
                Log.e("PrivateChatViewModel", "Failed to get current user details", exception)
                // TODO: Tampilkan pesan error kepada pengguna
            }
        }
    }

    private fun generateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    fun onNavigationDone() {
        _navigateToChat.value = null
    }
}