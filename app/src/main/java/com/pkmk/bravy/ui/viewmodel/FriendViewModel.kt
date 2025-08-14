package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.FriendInfo
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _allFriends = MutableLiveData<List<FriendInfo>>()

    // LiveData terpisah untuk setiap tab
    val friendList = MutableLiveData<List<FriendInfo>>()
    val receivedList = MutableLiveData<List<FriendInfo>>()
    val sentList = MutableLiveData<List<FriendInfo>>()

    private val _actionStatus = MutableLiveData<Result<String>>()
    val actionStatus: LiveData<Result<String>> = _actionStatus

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadFriends() {
        _isLoading.value = true
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.getFriendsData(currentUid)
            result.onSuccess { allFriends ->
                _allFriends.value = allFriends
                // Filter data untuk setiap tab
                friendList.value = allFriends.filter { it.status == "friend" }
                receivedList.value = allFriends.filter { it.status == "received" }
                sentList.value = allFriends.filter { it.status == "sent" }
            }.onFailure {
                _actionStatus.value = Result.failure(it)
            }
            _isLoading.value = false
        }
    }

    fun acceptRequest(senderUid: String) {
        viewModelScope.launch {
            val accepterUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.acceptFriendRequest(accepterUid, senderUid)
            result.onSuccess {
                _actionStatus.value = Result.success("Friend added!")
                loadFriends() // Muat ulang daftar setelah aksi
            }.onFailure { _actionStatus.value = Result.failure(it) }
        }
    }

    fun removeOrRejectFriendship(otherUid: String, isRejecting: Boolean) {
        viewModelScope.launch {
            val currentUid = auth.currentUser?.uid ?: return@launch
            val result = authRepository.removeFriendship(currentUid, otherUid)
            result.onSuccess {
                val message = if (isRejecting) "Request rejected" else "Friend removed"
                _actionStatus.value = Result.success(message)
                loadFriends() // Muat ulang daftar setelah aksi
            }.onFailure { _actionStatus.value = Result.failure(it) }
        }
    }
}