package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.AppNotification
import com.pkmk.bravy.data.source.FirebaseDataSource // Ganti dengan Repository jika ada
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val dataSource: FirebaseDataSource, // Idealnya lewat Repository
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _notifications = MutableLiveData<Result<List<AppNotification>>>()
    val notifications: LiveData<Result<List<AppNotification>>> = _notifications

    fun loadNotifications() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val notifs = dataSource.getUserNotifications(uid)
                _notifications.postValue(Result.success(notifs))
            } catch (e: Exception) {
                _notifications.postValue(Result.failure(e))
            }
        }
    }
}