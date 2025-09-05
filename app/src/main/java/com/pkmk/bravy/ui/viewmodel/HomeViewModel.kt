package com.pkmk.bravy.ui.viewmodel

import android.os.CountDownTimer
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pkmk.bravy.data.model.DailyMission
import com.pkmk.bravy.data.model.DailyMissionStatus
import com.pkmk.bravy.data.model.DailyMood
import com.pkmk.bravy.data.model.MissionType
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
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

    private val _dailyMissions = MutableLiveData<Result<List<DailyMission>>>()
    val dailyMissions: LiveData<Result<List<DailyMission>>> = _dailyMissions

    private val _learningProgress = MutableLiveData<Result<Triple<Int, Int, Int>>>()
    val learningProgress: LiveData<Result<Triple<Int, Int, Int>>> = _learningProgress

    private val _missionCountdown = MutableLiveData<String>()
    val missionCountdown: LiveData<String> = _missionCountdown

    private var countDownTimer: CountDownTimer? = null

    fun loadUserProfile() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    val userResult = authRepository.getUser(currentUser.uid)
                    _userProfile.postValue(userResult)

                    userResult.onSuccess { user ->
                        calculateTotalProgress(user)
                        loadDailyMissions(user)
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

    private fun loadDailyMissions(user: User) {
        viewModelScope.launch {
            val topicsResult = authRepository.getDailyMissionTopics()
            if (topicsResult.isFailure) {
                _dailyMissions.postValue(Result.failure(topicsResult.exceptionOrNull()!!))
                return@launch
            }

            val topics = topicsResult.getOrThrow()
            val today = Calendar.getInstance()
            val dayOfYear = today.get(Calendar.DAY_OF_YEAR)
            val topicToday = topics[dayOfYear % topics.size] // Rotasi topik harian

            val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val missionStatus = user.dailyMissionStatus

            // Periksa apakah misi perlu direset
            val currentStatus = if (missionStatus == null || missionStatus.date != todayDateString) {
                DailyMissionStatus(date = todayDateString) // Reset misi
            } else {
                missionStatus
            }

            val missions = listOf(
                DailyMission("1", "Daily Speaking Check-in", "Topic: $topicToday", currentStatus.completedMissions["SPEAKING"] == true, MissionType.SPEAKING),
                DailyMission("2", "Engage with the Community", "Create a post or leave a comment", currentStatus.completedMissions["COMMUNITY"] == true, MissionType.COMMUNITY),
                DailyMission("3", "Connect with a Friend", "Send an uplifting message", currentStatus.completedMissions["CHAT"] == true, MissionType.CHAT)
            )
            _dailyMissions.postValue(Result.success(missions))
        }
    }

    fun startDailyMissionCountdown() {
        // Hentikan timer lama jika ada untuk mencegah duplikasi
        countDownTimer?.cancel()

        // Hitung waktu hingga tengah malam
        val now = Calendar.getInstance()
        val endOfDay = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val millisUntilFinished = endOfDay.timeInMillis - now.timeInMillis

        countDownTimer = object : CountDownTimer(millisUntilFinished, 1000) {
            override fun onTick(millisLeft: Long) {
                // Format waktu menjadi HH:MM:SS
                val hours = TimeUnit.MILLISECONDS.toHours(millisLeft)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisLeft) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisLeft) % 60

                val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                _missionCountdown.postValue(formattedTime)
            }

            override fun onFinish() {
                _missionCountdown.postValue("00:00:00")
                // Muat ulang data misi saat timer habis untuk mereset
                loadUserProfile()
            }
        }.start()
    }

    fun stopDailyMissionCountdown() {
        countDownTimer?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        stopDailyMissionCountdown()
    }
}