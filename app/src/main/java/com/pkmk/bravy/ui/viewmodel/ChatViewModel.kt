package com.pkmk.bravy.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.source.FirebaseDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val firebaseDataSource: FirebaseDataSource
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _recentChatUsers = MutableLiveData<Result<List<Triple<User, String, Message?>>>>()
    val recentChatUsers: LiveData<Result<List<Triple<User, String, Message?>>>> = _recentChatUsers

    private val TAG = "ChatViewModel"

    fun loadRecentChatUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser == null) {
                    _recentChatUsers.value = Result.failure(Exception("No user logged in"))
                    _isLoading.value = false
                    return@launch
                }
                val chatIds = firebaseDataSource.getUserChats(currentUser.uid)
                Log.d(TAG, "Chat IDs: $chatIds")
                val usersWithChatIdsAndMessages = mutableListOf<Triple<User, String, Message?>>()
                for (chatId in chatIds) {
                    val participant = firebaseDataSource.getChatParticipant(chatId, currentUser.uid)
                    val lastMessage = firebaseDataSource.getLastChatMessage(chatId)
                    if (participant != null) {
                        usersWithChatIdsAndMessages.add(Triple(participant, chatId, lastMessage))
                    }
                }
                _recentChatUsers.value = Result.success(usersWithChatIdsAndMessages)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recent chat users: ${e.message}")
                if (e !is CancellationException) {
                    _recentChatUsers.value = Result.failure(e)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getLastMessage(chatId: String): LiveData<Message?> {
        val lastMessage = MutableLiveData<Message?>()
        viewModelScope.launch {
            try {
                val message = firebaseDataSource.getLastChatMessage(chatId)
                lastMessage.value = message
            } catch (e: Exception) {
                Log.e(TAG, "Error loading last message: ${e.message}")
                lastMessage.value = null
            }

        }
        return lastMessage
    }

    fun getLastTimestamp(chatId: String): LiveData<Long?> {
        val lastTimestamp = MutableLiveData<Long?>()
        viewModelScope.launch {
            try {
                val timestamp = firebaseDataSource.getLastMessageTimestamp(chatId)
                lastTimestamp.value = timestamp
            } catch (e: Exception) {
                Log.e(TAG, "Error loading last timestamp: ${e.message}")
                lastTimestamp.value = null
            }
        }
        return lastTimestamp
    }
}