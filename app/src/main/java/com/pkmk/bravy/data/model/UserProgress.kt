package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserProgress(
    val completed_sections: Map<String, Boolean> = emptyMap()
) : Parcelable