package com.pkmk.bravy.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import com.pkmk.bravy.data.source.FirebaseDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource,
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userProfile = MutableLiveData<Result<User>>()
    val userProfile: LiveData<Result<User>> = _userProfile

    private val _recentChats = MutableLiveData<Result<List<RecentChat>>>()
    val recentChats: LiveData<Result<List<RecentChat>>> = _recentChats

    private val _latestCommunityPost = MutableLiveData<Result<CommunityPostDetails?>>()
    val latestCommunityPost: LiveData<Result<CommunityPostDetails?>> = _latestCommunityPost

    private val _suggestedFriends = MutableLiveData<Result<List<User>>>()
    val suggestedFriends: LiveData<Result<List<User>>> = _suggestedFriends

    private val _friendActionStatus = MutableLiveData<Result<Unit>>()
    val friendActionStatus: LiveData<Result<Unit>> = _friendActionStatus

    private val TAG = "ChatViewModel"

    // Fungsi utama untuk memuat semua data awal dan memulai listener
    fun initializeData() {
        _isLoading.value = true
        viewModelScope.launch {
            // Jalankan tugas yang tidak perlu listener secara bersamaan
            val profileJob = async { loadUserProfile() }
            val friendsJob = async { loadSuggestedFriends() }
            awaitAll(profileJob, friendsJob) // Tunggu keduanya selesai

            // Setelah tugas awal selesai, mulai listener
            startListeningForLatestPost()
            startListeningForChatListChanges()

            delay(2000)
            _isLoading.postValue(false) // Selesaikan loading
        }
    }

    // Fungsi untuk memulai semua listener saat onResume
    fun startRealtimeListeners() {
        startListeningForLatestPost()
        startListeningForChatListChanges()
    }

    // Fungsi untuk menghentikan semua listener saat onPause
    fun stopRealtimeListeners() {
        stopListeningForLatestPost()
        stopListeningForChatListChanges()
    }

    private suspend fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val result = authRepository.getUser(currentUser.uid)
            _userProfile.postValue(result)
        } else {
            _userProfile.postValue(Result.failure(Exception("No user logged in")))
        }
    }

    private suspend fun loadSuggestedFriends() {
        // Sesi ditentukan oleh Bearer token; query kosong berarti "semua user
        // selain diri sendiri" yang dipakai sebagai saran teman.
        val result = authRepository.searchUsersBackend(query = "", limit = SUGGESTED_FRIENDS_LIMIT)
        _suggestedFriends.postValue(result)
    }

    private fun startListeningForLatestPost() {
        authRepository.listenForLatestCommunityPost { result ->
            result.onSuccess { post ->
                if (post == null) {
                    _latestCommunityPost.postValue(Result.success(null))
                    return@onSuccess
                }
                viewModelScope.launch {
                    authRepository.getUser(post.authorUid).onSuccess { author ->
                        _latestCommunityPost.postValue(Result.success(CommunityPostDetails(post, author)))
                    }.onFailure {
                        _latestCommunityPost.postValue(Result.failure(it))
                    }
                }
            }.onFailure {
                _latestCommunityPost.postValue(Result.failure(it))
            }
        }
    }

    private fun stopListeningForLatestPost() {
        authRepository.removeLatestPostListener()
    }

    private fun startListeningForChatListChanges() {
        val uid = auth.currentUser?.uid ?: return
        authRepository.listenForUserChats(uid) {
            Log.d(TAG, "Chat list changed, reloading recent chats.")
            loadRecentChatUsers() // Panggil fungsi load saat ada perubahan
        }
    }

    private fun stopListeningForChatListChanges() {
        authRepository.removeUserChatsListener()
    }

    private fun loadRecentChatUsers() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser ?: return@launch
                val chatIds = firebaseDataSource.getUserChats(currentUser.uid)
                val recentChatList = mutableListOf<RecentChat>()
                for (chatId in chatIds) {
                    val otherUserUid = firebaseDataSource.getParticipantUids(chatId).firstOrNull { it != currentUser.uid }
                    if (otherUserUid != null) {
                        val otherUser = firebaseDataSource.getUser(otherUserUid)
                        val lastMessage = firebaseDataSource.getLastChatMessage(chatId)
                        if (otherUser != null) {
                            recentChatList.add(RecentChat(otherUser, chatId, lastMessage))
                        }
                    }
                }
                recentChatList.sortByDescending { it.lastMessage?.timestamp }
                _recentChats.postValue(Result.success(recentChatList))
            } catch (e: Exception) {
                _recentChats.postValue(Result.failure(e))
            }
        }
    }

    fun sendFriendRequest(toUid: String) {
        viewModelScope.launch {
            // Sesi ditentukan oleh Bearer token, jadi uid pengirim tidak dikirim.
            val result = authRepository.sendFriendRequestBackend(toUid)
            _friendActionStatus.postValue(result)
        }
    }

    fun cancelFriendRequest(toUid: String) {
        viewModelScope.launch {
            // TODO(cancel-vs-reject): backend belum punya aksi "cancel" tersendiri,
            // sehingga pencabutan request yang masih terkirim memakai action=reject.
            // Pemilik token sudah menentukan sisi "from", jadi uid tidak dikirim.
            val result = authRepository.respondFriendRequestBackend(toUid, ACTION_REJECT)
            _friendActionStatus.postValue(result)
        }
    }

    fun toggleLikeOnLatestPost() {
        val currentUid = auth.currentUser?.uid ?: return
        val postDetails = _latestCommunityPost.value?.getOrNull() ?: return
        val post = postDetails.post

        viewModelScope.launch {
            // Panggil repository untuk mengubah data di Firebase
            val result = authRepository.toggleLikeOnPost(post.postId, currentUid)

            // Jika berhasil, perbarui LiveData secara manual untuk umpan balik instan
            if (result.isSuccess) {
                val newLikes = post.likes?.toMutableMap() ?: mutableMapOf()

                if (newLikes.containsKey(currentUid)) {
                    newLikes.remove(currentUid) // Unlike
                } else {
                    newLikes[currentUid] = true // Like
                }

                val updatedPost = post.copy(likes = newLikes)
                val updatedDetails = postDetails.copy(post = updatedPost)
                _latestCommunityPost.postValue(Result.success(updatedDetails))
            }
        }
    }

    private companion object {
        /** `action` untuk `PATCH /users/friends/respond`. */
        const val ACTION_REJECT = "reject"

        /** Jumlah saran teman yang diminta untuk daftar di layar chat. */
        const val SUGGESTED_FRIENDS_LIMIT = 5
    }
}