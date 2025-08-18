package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Comment(
    val commentId: String = "",
    val authorUid: String = "",
    val commentText: String = "",
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "image" or "audio"
    val timestamp: Long = 0L
) : Parcelable