package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DailyMission(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val isCompleted: Boolean = false,
    val type: MissionType
) : Parcelable

enum class MissionType {
    SPEAKING,
    COMMUNITY,
    CHAT
}