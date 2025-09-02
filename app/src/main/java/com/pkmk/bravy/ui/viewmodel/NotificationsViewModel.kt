package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.AppNotification
import com.pkmk.bravy.data.repository.AuthRepository // Pastikan import ini benar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _notifications = MutableLiveData<Result<List<AppNotification>>>()
    val notifications: LiveData<Result<List<AppNotification>>> = _notifications

    fun loadNotifications() {
        viewModelScope.launch {
            // Mengambil notifikasi dari repository
            val result = repository.getUserNotifications()
            _notifications.postValue(result)
        }
    }
}