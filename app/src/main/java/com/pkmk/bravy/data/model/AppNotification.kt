package com.pkmk.bravy.data.model

import android.os.Parcelable
import com.google.firebase.database.PropertyName
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppNotification(
    val id: String = "",
    val senderId: String? = null,

    val senderName: String? = null, // Ini akan null karena tidak ada di DB
    val senderImage: String? = null, // Ini akan null karena tidak ada di DB
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val referenceId: String? = null, // Ini akan null karena tidak ada di DB
    val timestamp: Long = 0L,

    @get:PropertyName("read") @set:PropertyName("read")
    var isRead: Boolean = false
) : Parcelable