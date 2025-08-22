package com.pkmk.bravy.data.model

import android.os.Parcelable
import com.google.firebase.database.IgnoreExtraProperties
import kotlinx.parcelize.Parcelize

@IgnoreExtraProperties
@Parcelize
data class DailyMood(
    val emotion: String = "",
    val timestamp: Long = 0L
) : Parcelable