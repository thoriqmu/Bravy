package com.pkmk.bravy.data.remote

import com.pkmk.bravy.data.remote.dto.AvatarResponse
import com.pkmk.bravy.data.remote.dto.BackendFriend
import com.pkmk.bravy.data.remote.dto.BackendUser
import com.pkmk.bravy.data.remote.dto.BackendUserSummary
import com.pkmk.bravy.data.remote.dto.ChatMessageDto
import com.pkmk.bravy.data.remote.dto.ConversationDto
import com.pkmk.bravy.data.remote.dto.CreateConversationRequest
import com.pkmk.bravy.data.remote.dto.FcmTokenRequest
import com.pkmk.bravy.data.remote.dto.LoginRequest
import com.pkmk.bravy.data.remote.dto.LoginResponse
import com.pkmk.bravy.data.remote.dto.RefreshTokenRequest
import com.pkmk.bravy.data.remote.dto.RefreshTokenResponse
import com.pkmk.bravy.data.remote.dto.RegisterRequest
import com.pkmk.bravy.data.remote.dto.RegisterResponse
import com.pkmk.bravy.data.remote.dto.ResendVerificationRequest
import com.pkmk.bravy.data.remote.dto.RespondFriendRequestBody
import com.pkmk.bravy.data.remote.dto.SendFriendRequestBody
import com.pkmk.bravy.data.remote.dto.SendMessageRequest
import com.pkmk.bravy.data.remote.dto.UpdateProfileRequest
import com.pkmk.bravy.data.remote.dto.UserProfileDto
import com.pkmk.bravy.data.remote.dto.VerifyEmailRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Kontrak REST backend Bravy. Endpoint yang butuh autentikasi memakai header
 * `Authorization: Bearer <accessToken>` yang disisipkan oleh [AuthInterceptor],
 * bukan parameter eksplisit.
 */
interface BravyApiService {

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<RegisterResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @POST("api/v1/auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): ApiResponse<Unit>

    @POST("api/v1/auth/resend-verification")
    suspend fun resendVerification(@Body body: ResendVerificationRequest): ApiResponse<Unit>

    /**
     * Menukar refresh token dengan pasangan token baru. Dipanggil [TokenAuthenticator]
     * tanpa header Authorization agar token kedaluwarsa tidak ikut terkirim.
     */
    @POST("api/v1/auth/refresh-token")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): ApiResponse<RefreshTokenResponse>

    /** Mencabut refresh token di server; kegagalannya tidak menghalangi logout lokal. */
    @POST("api/v1/auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    /** Profil ringkas user yang sedang login (berdasarkan access token). */
    @GET("api/v1/auth/me")
    suspend fun getMe(): ApiResponse<BackendUser>

    @GET("api/v1/users/profile")
    suspend fun getProfile(): ApiResponse<UserProfileDto>

    @PATCH("api/v1/users/profile")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): ApiResponse<UserProfileDto>

    @Multipart
    @POST("api/v1/users/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): ApiResponse<AvatarResponse>

    /** Mencari user lain berdasarkan nama atau email; `q` wajib diisi. */
    @GET("api/v1/users/search")
    suspend fun searchUsers(@Query("q") query: String): ApiResponse<List<BackendUserSummary>>

    @GET("api/v1/users/friends")
    suspend fun getFriends(): ApiResponse<List<BackendFriend>>

    @POST("api/v1/users/friends/request")
    suspend fun sendFriendRequest(@Body body: SendFriendRequestBody): ApiResponse<Unit>

    @PATCH("api/v1/users/friends/respond")
    suspend fun respondFriendRequest(@Body body: RespondFriendRequestBody): ApiResponse<Unit>

    @PATCH("api/v1/users/fcm-token")
    suspend fun updateFcmToken(@Body body: FcmTokenRequest): ApiResponse<Unit>

    // --- Chat privat (`/api/v1/chat`) ---

    /** Seluruh percakapan milik pemilik token, terbaru lebih dulu. */
    @GET("api/v1/chat")
    suspend fun getConversations(): ApiResponse<List<ConversationDto>>

    /**
     * Membuat percakapan dengan [CreateConversationRequest.participantId], atau
     * mengembalikan yang sudah ada. Backend menolak bila id sama dengan diri sendiri.
     */
    @POST("api/v1/chat")
    suspend fun createConversation(
        @Body body: CreateConversationRequest
    ): ApiResponse<ConversationDto>

    /**
     * Satu halaman pesan (50 per halaman, terbaru lebih dulu). Backend juga
     * menandai pesan lawan sebagai terbaca setiap kali endpoint ini dipanggil.
     */
    @GET("api/v1/chat/{conversationId}/messages")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("page") page: Int = DEFAULT_MESSAGE_PAGE
    ): ApiResponse<List<ChatMessageDto>>

    /** Mengirim teks atau media; `clientMessageId` membuat pengiriman idempoten. */
    @POST("api/v1/chat/{conversationId}/messages")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body body: SendMessageRequest
    ): ApiResponse<ChatMessageDto>

    /** Menandai pesan lawan bicara sebagai terbaca. `data` pada respons selalu null. */
    @PATCH("api/v1/chat/{conversationId}/read")
    suspend fun markConversationRead(
        @Path("conversationId") conversationId: String
    ): ApiResponse<Unit>

    companion object {
        /** Halaman pertama pesan; backend memakai penomoran mulai dari 1. */
        const val DEFAULT_MESSAGE_PAGE = 1
    }
}
