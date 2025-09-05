package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DailyMissionStatus(
    val date: String = "", // Format: YYYY-MM-DD
    val completedMissions: Map<String, Boolean> = emptyMap() // Key: "SPEAKING", "COMMUNITY", "CHAT"
) : Parcelable