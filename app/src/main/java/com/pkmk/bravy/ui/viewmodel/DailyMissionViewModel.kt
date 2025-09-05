// Lokasi: ui/viewmodel/DailyMissionViewModel.kt
package com.pkmk.bravy.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkmk.bravy.data.model.DailyMissionStatus
import com.pkmk.bravy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class DailyMissionViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    fun completeSpeakingMission(uid: String, emotion: String, confidence: Int, wordCount: Int) {
        viewModelScope.launch {
            val userResult = repository.getUser(uid)
            if (userResult.isSuccess) {
                val user = userResult.getOrThrow()
                val today = Calendar.getInstance()
                val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Cek status misi hari ini
                val currentStatus = user.dailyMissionStatus?.takeIf { it.date == todayDateString }
                    ?: DailyMissionStatus(date = todayDateString)

                // Cek apakah misi speaking sudah selesai hari ini untuk mencegah streak ganda
                val wasAlreadyCompleted = currentStatus.completedMissions["SPEAKING"] == true

                // Update status misi
                val updatedMissions = currentStatus.completedMissions.toMutableMap()
                updatedMissions["SPEAKING"] = true
                val newStatus = currentStatus.copy(completedMissions = updatedMissions)

                // Hitung streak baru
                val newStreak = if (!wasAlreadyCompleted) {
                    val lastCheckIn = user.lastSpeakingTimestamp
                    val lastCheckInCal = Calendar.getInstance().apply { timeInMillis = lastCheckIn }

                    when {
                        isYesterday(today, lastCheckInCal) -> user.streak + 1
                        isSameDay(today, lastCheckInCal) -> user.streak
                        else -> 1
                    }
                } else {
                    user.streak // Streak tidak berubah jika misi sudah selesai hari ini
                }

                repository.updateUserMissionsAndStreak(uid, newStatus, emotion, System.currentTimeMillis(), newStreak, confidence, wordCount)
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