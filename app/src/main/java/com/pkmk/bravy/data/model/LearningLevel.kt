package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LearningLevel(
    val levelId: String = "",
    val title: String = "",
    val sections: Map<String, LearningSection> = emptyMap()
) : Parcelable