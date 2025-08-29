package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.DailyMood
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _userProfile = MutableLiveData<Result<User>>()
    val userProfile: LiveData<Result<User>> get() = _userProfile

    private val _moodUpdateStatus = MutableLiveData<Result<Unit>>()
    val moodUpdateStatus: LiveData<Result<Unit>> = _moodUpdateStatus

    fun loadUserProfile() {
        _isLoading.value = true // Mulai loading
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    val result = authRepository.getUser(currentUser.uid)
                    _userProfile.postValue(result)
                } else {
                    _userProfile.postValue(Result.failure(Exception("No user logged in")))
                }
            } catch (e: Exception) {
                _userProfile.postValue(Result.failure(e))
            } finally {
                kotlinx.coroutines.delay(3000)
                _isLoading.value = false // Selesaikan loading
            }
        }
    }

    fun checkInDailyMood(emotion: String) {
        val user = _userProfile.value?.getOrNull() ?: return
        val uid = user.uid

        viewModelScope.launch {
            val today = Calendar.getInstance()
            val lastCheckIn = user.dailyMood?.timestamp?.let {
                Calendar.getInstance().apply { timeInMillis = it }
            }

            // --- PERBAIKAN LOGIKA STREAK ---
            val newStreak = when {
                // Jika terakhir check-in adalah kemarin, lanjutkan streak
                lastCheckIn != null && isYesterday(today, lastCheckIn) -> user.streak + 1
                // Jika sudah check-in hari ini, streak tidak berubah
                lastCheckIn != null && isSameDay(today, lastCheckIn) -> user.streak
                // Jika tidak, mulai streak baru dari 1
                else -> 1
            }

            val newMood = DailyMood(emotion, today.timeInMillis)
            val result = authRepository.updateUserStreakAndMood(uid, newStreak, newMood)

            _moodUpdateStatus.postValue(result)
            // Muat ulang profil untuk memperbarui UI
            if (result.isSuccess) {
                // Tidak perlu panggil loadUserProfile() lagi karena UI sudah diupdate secara instan
            }
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, lastCheckIn: Calendar): Boolean {
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return lastCheckIn.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                lastCheckIn.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    }
}