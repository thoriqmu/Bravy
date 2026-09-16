package com.pkmk.bravy.data.remote.socket

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Jembatan antara pesan chat FCM dan layar yang sedang terbuka.
 *
 * Pesan yang tiba saat aplikasi terlihat sudah tampil lewat soket, sehingga
 * tidak perlu notifikasi; sebagai gantinya [MyFirebaseMessagingService] meminta
 * layar terkait memuat ulang datanya lewat kelas ini.
 *
 * Singleton karena service FCM dan ViewModel harus berbagi aliran yang sama, dan
 * sengaja tanpa state: permintaan yang datang saat tidak ada pelanggan akan
 * dibuang (perilaku bawaan [MutableSharedFlow] dengan `replay = 0`), yang aman
 * karena riwayat selalu bisa diambil ulang lewat REST begitu layar dibuka.
 */
@Singleton
class ChatNotificationSync @Inject constructor() {

    /**
     * `extraBufferCapacity` dipakai agar `tryEmit` dari service FCM tidak
     * memblokir thread pemanggil.
     */
    private val _refreshRequests = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = REFRESH_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Id percakapan yang perlu dimuat ulang. */
    val refreshRequests: SharedFlow<String> = _refreshRequests.asSharedFlow()

    /** Meminta layar yang menampilkan [conversationId] memuat ulang datanya. */
    fun requestRefresh(conversationId: String) {
        if (conversationId.isBlank()) return
        _refreshRequests.tryEmit(conversationId)
    }

    private companion object {
        const val REFRESH_BUFFER_CAPACITY = 8
    }
}
