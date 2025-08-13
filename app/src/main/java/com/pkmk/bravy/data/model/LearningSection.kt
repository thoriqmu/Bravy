package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LearningSection(
    val sectionId: String = "",
    val title: String = "",
    val order: Int = 0,
    // Menggunakan list dari scene, bukan properti tunggal
    val scenes: List<LearningScene> = emptyList(),
    var isLocked: Boolean = true // Untuk status UI
) : Parcelable