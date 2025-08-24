package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CommentDetails(
    val comment: Comment,
    val author: User
) : Parcelable