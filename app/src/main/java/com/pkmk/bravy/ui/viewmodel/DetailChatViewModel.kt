package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.pkmk.bravy.data.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailChatViewModel @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> = _messages

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var messagesListener: ValueEventListener? = null
    private lateinit var messagesRef: DatabaseReference

    /**
     * Memasang listener untuk mengambil pesan secara real-time dari sebuah chat.
     */
    fun listenForMessages(chatId: String) {
        messagesRef = database.getReference("private_chats/$chatId/messages")

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messageList = snapshot.children.mapNotNull {
                    // Mengambil pesan dan menyalin messageId dari key snapshot
                    it.getValue(Message::class.java)?.copy(messageId = it.key ?: "")
                }
                _messages.value = messageList
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = "Failed to load messages: ${error.message}"
            }
        }
        messagesRef.addValueEventListener(messagesListener!!)
    }

    /**
     * Mengirim pesan baru ke database.
     */
    fun sendMessage(chatId: String, content: String, type: String) {
        viewModelScope.launch {
            val currentUserId = auth.currentUser?.uid ?: return@launch
            val messageRef = database.getReference("private_chats/$chatId/messages").push()
            val messageId = messageRef.key ?: return@launch

            val message = Message(
                messageId = messageId,
                sender_uid = currentUserId,
                content = content,
                type = type,
                timestamp = System.currentTimeMillis()
            )
            messageRef.setValue(message)
        }
    }

    /**
     * Membersihkan listener saat ViewModel dihancurkan untuk mencegah memory leak.
     */
    override fun onCleared() {
        super.onCleared()
        if (::messagesRef.isInitialized && messagesListener != null) {
            messagesRef.removeEventListener(messagesListener!!)
        }
    }
}