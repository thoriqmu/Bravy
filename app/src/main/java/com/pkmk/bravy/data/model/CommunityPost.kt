package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommunityPost(
    val postId: String = "",
    val authorUid: String = "",
    val title: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val timestamp: Long = 0L,
    val likes: Map<String, Boolean> = emptyMap(), // Key: UID, Value: true
    val comments: Map<String, Comment> = emptyMap()
) : Parcelable