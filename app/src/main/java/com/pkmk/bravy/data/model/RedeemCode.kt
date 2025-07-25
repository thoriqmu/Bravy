package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RedeemCode(
    val code: String = "",
    val isUsed: Boolean = false,
    val createdAt: String = ""
) : Parcelable