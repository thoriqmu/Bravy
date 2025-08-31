package com.pkmk.bravy.data.model

import android.os.Parcelable
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@IgnoreExtraProperties
@Parcelize
data class User(
    val uid: String = "",
    val name: String = "",
    val email: String? = null,
    val redeemCode: String? = null,
    val bio: String? = null,
    val image: String? = null,
    val chats: Map<String, Boolean>? = null,
    val user_progress: Map<String, UserProgress>? = null,
    val friends: Map<String, Friend>? = null,
    val streak: Int = 0,
    val dailyMood: DailyMood? = null,
    val points: Int = 0,
    val createdAt: Long = 0L
) : Parcelable