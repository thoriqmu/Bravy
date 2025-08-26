package com.pkmk.bravy.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppNotification(
    val id: String = "",
    val recipientUid: String = "", // UID pengguna yang menerima notifikasi
    val senderUid: String? = null, // UID pengirim (opsional)
    val senderName: String? = null, // Nama pengirim (untuk kemudahan tampilan)
    val senderImage: String? = null, // Gambar profil pengirim (opsional)
    val type: String = "", // "PROGRESS", "CHAT_PRIVATE", "CHAT_COMMUNITY_REPLY"
    val title: String = "",
    val message: String = "",
    val referenceId: String? = null, // ID dari post, chat, atau level
    val timestamp: Long = 0L,
    var isRead: Boolean = false
) : Parcelable