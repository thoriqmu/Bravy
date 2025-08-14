package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FriendInfo(
    val user: User,
    val status: String // "friend", "received", "sent"
) : Parcelable