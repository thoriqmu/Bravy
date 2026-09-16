package com.pkmk.bravy.data.remote

import com.pkmk.bravy.data.remote.dto.RefreshTokenRequest
import com.pkmk.bravy.data.remote.dto.RefreshTokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Kontrak minimal untuk menukar refresh token, dipakai [TokenAuthenticator].
 *
 * Dipisahkan dari [BravyApiService] dan sengaja memakai [Call] (bukan `suspend`)
 * karena [TokenAuthenticator] berjalan di thread OkHttp. Retrofit untuk antarmuka
 * ini dibangun tanpa Authenticator, sehingga tidak ada siklus dependensi
 * (OkHttpClient → Authenticator → Retrofit → OkHttpClient) saat refresh dipicu.
 */
interface RefreshTokenApi {

    @POST("api/v1/auth/refresh-token")
    fun refreshToken(@Body body: RefreshTokenRequest): Call<ApiResponse<RefreshTokenResponse>>
}
