package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class LearningScene(
    val sceneType: String = "",
    val videoUrl: String? = null,
    val prompt: String? = null,
    val duration: Int? = null,
    val responses: Map<String, String>? = null,
    val text: String? = null,
    val keySentence: String? = null // <- Tambahkan ini
) : Parcelable