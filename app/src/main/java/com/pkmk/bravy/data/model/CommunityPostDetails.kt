package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommunityPostDetails(
    val post: CommunityPost,
    val author: User
) : Parcelable