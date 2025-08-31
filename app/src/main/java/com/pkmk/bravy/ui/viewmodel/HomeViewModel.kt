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

    private val _learningProgress = MutableLiveData<Result<Triple<Int, Int, Int>>>()
    val learningProgress: LiveData<Result<Triple<Int, Int, Int>>> = _learningProgress

    fun loadUserProfile() {
        _isLoading.value = true // Mulai loading
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    val userResult = authRepository.getUser(currentUser.uid)
                    _userProfile.postValue(userResult)

                    userResult.onSuccess { user ->
                        calculateTotalProgress(user)
                    }
                } else {
                    val error = Result.failure<User>(Exception("No user logged in"))
                    _userProfile.postValue(error)
                    _learningProgress.postValue(Result.failure(error.exceptionOrNull()!!))
                }
            } catch (e: Exception) {
                _userProfile.postValue(Result.failure(e))
                _learningProgress.postValue(Result.failure(e))
            } finally {
                // Tambahkan delay 2 detik (2000 milidetik)
                kotlinx.coroutines.delay(2000)
                _isLoading.postValue(false) // Selesaikan loading setelah delay
            }
        }
    }

    // --- TAMBAHKAN FUNGSI BARU UNTUK MENGHITUNG PROGRESS ---
    private fun calculateTotalProgress(user: User) {
        viewModelScope.launch {
            val levelsResult = authRepository.getLearningLevels()
            levelsResult.onSuccess { levelsSnapshot ->
                var totalSections = 0
                var completedSections = 0

                // Hitung total section dari semua level
                for (levelSnap in levelsSnapshot.children) {
                    totalSections += levelSnap.child("sections").children.count()
                }

                // Hitung section yang sudah diselesaikan oleh user
                user.user_progress?.values?.forEach { progress ->
                    completedSections += progress.completed_sections.count { it.value }
                }

                // Hitung persentase
                val percentage = if (totalSections > 0) {
                    (completedSections * 100) / totalSections
                } else {
                    0
                }

                _learningProgress.postValue(Result.success(Triple(percentage, completedSections, totalSections)))

            }.onFailure {
                _learningProgress.postValue(Result.failure(it))
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