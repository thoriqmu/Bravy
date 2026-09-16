package com.pkmk.bravy.data.remote

import com.pkmk.bravy.BuildConfig
import com.pkmk.bravy.data.remote.dto.RefreshTokenRequest
import com.pkmk.bravy.data.remote.dto.RefreshTokenResponse
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pemanggil `auth/refresh-token` untuk [TokenAuthenticator].
 *
 * Memakai Retrofit sendiri yang dibangun dari OkHttpClient polos (tanpa
 * Authenticator), sehingga kegagalan refresh tidak memicu refresh lagi tanpa
 * henti. Sepuluh detik sudah cukup: request-nya kecil dan hanya bertukar token.
 */
@Singleton
class RefreshTokenClient @Inject constructor() {

    private val api: RefreshTokenApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.BRAVY_BASE_URL)
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RefreshTokenApi::class.java)
    }

    /** Blocking; dipanggil dari thread OkHttp di dalam [TokenAuthenticator]. */
    fun refresh(refreshToken: String): Response<ApiResponse<RefreshTokenResponse>> {
        return api.refreshToken(RefreshTokenRequest(refreshToken)).execute()
    }
}
