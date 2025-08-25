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
    val userProfile: LiveData<Result<User>> get() = _userProfile

    private val _recentChats = MutableLiveData<Result<List<RecentChat>>>()
    val recentChats: LiveData<Result<List<RecentChat>>> = _recentChats

    private val _latestCommunityPost = MutableLiveData<Result<CommunityPostDetails?>>()
    val latestCommunityPost: LiveData<Result<CommunityPostDetails?>> = _latestCommunityPost

    private val _suggestedFriends = MutableLiveData<Result<List<User>>>()
    val suggestedFriends: LiveData<Result<List<User>>> = _suggestedFriends

    private val _friendActionStatus = MutableLiveData<Result<Unit>>()
    val friendActionStatus: LiveData<Result<Unit>> = _friendActionStatus

    private val TAG = "ChatViewModel"

    fun loadUserProfile() {
        viewModelScope.launch {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val result = authRepository.getUser(currentUser.uid)
                _userProfile.postValue(result)
            } else {
                _userProfile.postValue(Result.failure(Exception("No user logged in")))
            }
        }
    }

    fun loadRecentChatUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    _recentChats.value = Result.failure(Exception("User not logged in"))
                    return@launch
                }

                val chatIds = firebaseDataSource.getUserChats(currentUser.uid)
                val recentChatList = mutableListOf<RecentChat>()

                for (chatId in chatIds) {
                    val participantUids = firebaseDataSource.getParticipantUids(chatId)
                    val otherUserUid = participantUids.firstOrNull { it != currentUser.uid }

                    if (otherUserUid != null) {
                        val otherUser = firebaseDataSource.getUser(otherUserUid)
                        val lastMessage = firebaseDataSource.getLastChatMessage(chatId)

                        if (otherUser != null) {
                            recentChatList.add(RecentChat(otherUser, chatId, lastMessage))
                        }
                    }
                }

                recentChatList.sortByDescending { it.lastMessage?.timestamp }
                _recentChats.value = Result.success(recentChatList)

            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _recentChats.value = Result.failure(e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadLatestCommunityPost() {
        viewModelScope.launch {
            val result = authRepository.getAllCommunityPostsWithDetails()
            result.onSuccess { posts ->
                // Ambil post pertama (yang terbaru), atau null jika tidak ada post
                _latestCommunityPost.postValue(Result.success(posts.firstOrNull()))
            }.onFailure {
                _latestCommunityPost.postValue(Result.failure(it))
            }
        }
    }

    fun loadSuggestedFriends() {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.getSuggestedFriends(currentUid, 3)
            _suggestedFriends.postValue(result)
        }
    }

    fun sendFriendRequest(toUid: String) {
        viewModelScope.launch {
            val fromUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.sendFriendRequest(fromUid, toUid)
            _friendActionStatus.postValue(result)
        }
    }

    fun cancelFriendRequest(toUid: String) {
        viewModelScope.launch {
            val fromUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.cancelFriendRequest(fromUid, toUid)
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
}