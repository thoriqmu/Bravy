package com.pkmk.bravy.data.remote

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Menangani respons 401 dengan menukar refresh token lalu mengulang request.
 *
 * Backend memberlakukan rotasi refresh token, jadi dua request yang gagal
 * bersamaan tidak boleh me-refresh sendiri-sendiri: refresh pertama membuat
 * token milik yang kedua tidak berlaku lagi. Karena itu seluruh proses
 * dilindungi [mutex] (single-flight) dan request yang menunggu memakai token
 * hasil refresh tersebut, bukan memulai refresh baru.
 *
 * Bila refresh ditolak, sesi diakhiri ([SessionManager.endSession]) dan request
 * asli dibiarkan gagal — Authenticator mengembalikan null.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshTokenClient: RefreshTokenClient,
    private val sessionManager: SessionManager
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Refresh hanya masuk akal untuk 401 dari endpoint terproteksi. Respons
        // lain (mis. 403) dan request tanpa Authorization tidak diulang.
        if (response.code != HTTP_UNAUTHORIZED) return null
        if (response.request.header(HEADER_AUTHORIZATION) == null) return null

        // Request yang sudah pernah diulang dan masih 401 tidak diulang lagi,
        // agar tidak terjadi percobaan tanpa henti.
        if (responseCount(response) >= MAX_ATTEMPTS) return null

        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            sessionManager.endSession()
            return null
        }

        val accessToken = runBlocking {
            mutex.withLock {
                refreshAccessToken(accessTokenInFlight = response.request.accessTokenHeader(), refreshToken = refreshToken)
            }
        } ?: return null

        return response.request.newBuilder()
            .header(HEADER_AUTHORIZATION, "Bearer $accessToken")
            .build()
    }

    /**
     * Menghasilkan access token yang layak dipakai. Bila token di penyimpanan
     * sudah berbeda dari yang dipakai request yang gagal, berarti request lain
     * baru saja me-refresh dan hasilnya bisa langsung dipakai.
     */
    private fun refreshAccessToken(accessTokenInFlight: String?, refreshToken: String): String? {
        val currentToken = tokenStore.getAccessToken()
        if (!currentToken.isNullOrBlank() && currentToken != accessTokenInFlight) {
            return currentToken
        }

        val storedRefreshToken = tokenStore.getRefreshToken()
        if (storedRefreshToken.isNullOrBlank()) {
            sessionManager.endSession()
            return null
        }

        return try {
            val response = refreshTokenClient.refresh(storedRefreshToken)
            val body = response.body()
            val data = body?.data
            if (!response.isSuccessful || body?.success != true || data == null) {
                Log.w(TAG, "Refresh token ditolak (HTTP ${response.code()}), mengakhiri sesi")
                sessionManager.endSession()
                null
            } else {
                tokenStore.saveTokens(data.accessToken, data.refreshToken)
                data.accessToken
            }
        } catch (e: Exception) {
            // Gangguan jaringan bukan alasan membuang sesi: token masih mungkin
            // valid dan permintaan berikutnya akan mencoba refresh lagi.
            Log.e(TAG, "Refresh token gagal karena kesalahan jaringan: ${e.message}")
            null
        }
    }

    /** Menghitung berapa kali request ini sudah diulang lewat Authenticator. */
    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun Request.accessTokenHeader(): String? {
        return header(HEADER_AUTHORIZATION)?.removePrefix(BEARER_PREFIX)
    }

    private companion object {
        const val TAG = "TokenAuthenticator"
        const val HTTP_UNAUTHORIZED = 401
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
        const val MAX_ATTEMPTS = 2
    }
}
