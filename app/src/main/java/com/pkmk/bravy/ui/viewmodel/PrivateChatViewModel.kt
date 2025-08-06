package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.data.source.FirebaseDataSource // Asumsi Anda memiliki data source ini
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivateChatViewModel @Inject constructor(
    private val dataSource: FirebaseDataSource,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _recentChats = MutableLiveData<Result<List<RecentChat>>>()
    val recentChats: LiveData<Result<List<RecentChat>>> = _recentChats

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Memuat semua percakapan terakhir untuk pengguna yang sedang login.
     */
    fun loadRecentChats() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentUser = auth.currentUser
            if (currentUser == null) {
                _recentChats.value = Result.failure(Exception("User not logged in."))
                _isLoading.value = false
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
            } finally {
                _isLoading.value = false
            }
        }
    }
}