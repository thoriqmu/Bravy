package com.pkmk.bravy.data.remote

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sumber kebenaran tunggal untuk berakhirnya sesi backend, baik karena pengguna
 * menekan tombol keluar maupun karena refresh token ditolak server.
 *
 * [TokenAuthenticator] tidak bisa menavigasi ke layar login sendiri, jadi ia
 * memberi sinyal lewat [loggedOut] dan observer di UI yang memindahkan pengguna.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore
) {
    private val _loggedOut = MutableLiveData(false)
    val loggedOut: LiveData<Boolean> get() = _loggedOut

    /** Sesi backend tidak dapat dilanjutkan: token dibuang agar tidak dipakai lagi. */
    fun endSession() {
        tokenStore.clear()
        _loggedOut.postValue(true)
    }

    /** Menandai bahwa token sudah dibersihkan oleh pemanggil (mis. logout sukses). */
    fun notifyLoggedOut() {
        _loggedOut.postValue(true)
    }

    /** Mencegah observer lama ikut terpicu pada sesi berikutnya. */
    fun acknowledgeLoggedOut() {
        _loggedOut.postValue(false)
    }
}
