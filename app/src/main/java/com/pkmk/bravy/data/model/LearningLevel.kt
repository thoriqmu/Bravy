package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LearningLevel(
    val levelId: String = "",
    val title: String = "",
    val description: String = "",
    val minPoints: Int = 0,
    val status: String = "coming soon",
    val thumbnail: String = "",
    val sections: Map<String, LearningSection> = emptyMap(),
    var isLocked: Boolean = true,
    var thumbnailUrl: String? = null
) : Parcelable