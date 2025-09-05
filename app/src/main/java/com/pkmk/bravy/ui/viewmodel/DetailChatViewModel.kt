package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.pkmk.bravy.data.model.ChatItem // <-- Import baru
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.MissionType
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DetailChatViewModel @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository
) : ViewModel() {

    // --- PERUBAHAN 1: Ganti tipe LiveData ---
    private val _chatItems = MutableLiveData<List<ChatItem>>()
    val chatItems: LiveData<List<ChatItem>> = _chatItems

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var messagesListener: ValueEventListener? = null
    private lateinit var messagesRef: DatabaseReference

    private val _otherUser = MutableLiveData<User?>()
    val otherUser: LiveData<User?> = _otherUser

    fun setOtherUser(user: User) {
        _otherUser.value = user
    }

    fun listenForMessages(chatId: String) {
        messagesRef = database.getReference("private_chats/$chatId/messages")

        messagesListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messageList = snapshot.children.mapNotNull {
                    it.getValue(Message::class.java)?.copy(messageId = it.key ?: "")
                }
                // --- PERUBAHAN 2: Proses daftar pesan untuk membuat daftar gabungan ---
                _chatItems.value = createChatListWithSeparators(messageList)
            }

            override fun onCancelled(error: DatabaseError) {
                _error.value = "Failed to load messages: ${error.message}"
            }
        }
        messagesRef.addValueEventListener(messagesListener!!)
    }

    // --- PERUBAHAN 3: Fungsi baru untuk menyisipkan pembatas tanggal ---
    private fun createChatListWithSeparators(messages: List<Message>): List<ChatItem> {
        val itemsWithSeparators = mutableListOf<ChatItem>()
        if (messages.isEmpty()) return itemsWithSeparators

        // Urutkan pesan berdasarkan timestamp untuk memastikan urutan benar
        val sortedMessages = messages.sortedBy { it.timestamp }

        for (i in sortedMessages.indices) {
            val currentMessage = sortedMessages[i]
            val prevMessage = if (i > 0) sortedMessages[i - 1] else null

            val needsSeparator = prevMessage == null || !isSameDay(currentMessage.timestamp, prevMessage.timestamp)

            if (needsSeparator) {
                itemsWithSeparators.add(ChatItem.DateSeparatorItem(formatDateSeparator(currentMessage.timestamp)))
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
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR)) {
            return "Today"
        }

        // Cek apakah kemarin
        now.add(Calendar.DAY_OF_YEAR, -1)
        if (now.get(Calendar.DAY_OF_YEAR) == messageCalendar.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == messageCalendar.get(Calendar.YEAR)) {
            return "Yesterday"
        }

        // Cek apakah dalam seminggu terakhir (sebelum kemarin)
        now.add(Calendar.DAY_OF_YEAR, 5) // Reset ke 6 hari yang lalu dari hari ini
        if (messageCalendar.after(now)) {
            return SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp)) // e.g., "Monday"
        }

        // Jika lebih lama dari seminggu
        return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(timestamp)) // e.g., "August 1, 2025"
    }

    // Fungsi BARU untuk memuat data pengguna berdasarkan ID
    fun loadChatDetails(otherUserId: String) {
        viewModelScope.launch {
            val result = authRepository.getUser(otherUserId)
            result.onSuccess {
                _otherUser.postValue(it)
            }.onFailure {
                _error.postValue("Failed to load user details.")
            }
        }
    }

    fun sendMessage(chatId: String, content: String, type: String) {
        // FUNGSI INI SEKARANG JAUH LEBIH SEDERHANA
        viewModelScope.launch {
            val currentUserId = auth.currentUser?.uid ?: return@launch
            val messageRef = database.getReference("private_chats/$chatId/messages").push()

            val message = Message(
                messageId = messageRef.key ?: "",
                sender_uid = currentUserId,
                content = content,
                type = type,
                timestamp = System.currentTimeMillis()
            )
            messageRef.setValue(message)
            authRepository.completeDailyMission(MissionType.CHAT)
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::messagesRef.isInitialized && messagesListener != null) {
            messagesRef.removeEventListener(messagesListener!!)
        }
    }
}