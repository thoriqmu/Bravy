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

    suspend fun completeSpeakingMission(
        uid: String,
        emotion: String,
        confidence: Int,
        wordCount: Int
    ): Result<Unit> = runCatching {
        val userResult = repository.getUser(uid)
        if (!userResult.isSuccess) error("getUser failed: ${userResult.exceptionOrNull()?.message}")

        val user = userResult.getOrThrow()

        val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val currentStatus = user.dailyMissionStatus?.takeIf { it.date == todayDateString }
            ?: DailyMissionStatus(date = todayDateString)

        val wasAlreadyCompleted = currentStatus.completedMissions["SPEAKING"] == true
        val updatedMissions = currentStatus.completedMissions.toMutableMap().apply {
            this["SPEAKING"] = true
        }
        val newStatus = currentStatus.copy(completedMissions = updatedMissions)

        val newStreak = if (!wasAlreadyCompleted) {
            val lastCheckIn = user.lastSpeakingTimestamp ?: 0L
            val lastCheckInCal = Calendar.getInstance().apply { timeInMillis = lastCheckIn }
            val today = Calendar.getInstance()
            when {
                isYesterday(today, lastCheckInCal) -> user.streak + 1
                isSameDay(today, lastCheckInCal) -> user.streak
                else -> 1
            }
        } else user.streak

        repository.updateUserMissionsAndStreak(
            uid, newStatus, emotion, System.currentTimeMillis(),
            newStreak, confidence, wordCount
        )
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