package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Message(
    val messageId: String = "",
    val sender_uid: String = "",
    val type: String = "text", // "text", "image", "audio"
    val content: String = "",
    val timestamp: Long = 0L,
    val reply_to: String? = null
) : Parcelable