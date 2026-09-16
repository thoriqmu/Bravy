package com.pkmk.bravy.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Body untuk `POST /api/v1/auth/register`. */
data class RegisterRequest(
    @SerializedName("fullName") val fullName: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

/** `data` pada respons register: `{ "user": { ... } }`. */
data class RegisterResponse(
    @SerializedName("user") val user: BackendUser
)

/** Body untuk `POST /api/v1/auth/login`. `identifier` = email atau username. */
data class LoginRequest(
    @SerializedName("identifier") val identifier: String,
    @SerializedName("password") val password: String
)

/** `data` pada respons login. */
data class LoginResponse(
    @SerializedName("user") val user: BackendUser,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String
)

/** Body untuk `POST /api/v1/auth/verify-email`. */
data class VerifyEmailRequest(
    @SerializedName("token") val token: String
)

/** Body untuk `POST /api/v1/auth/resend-verification`. */
data class ResendVerificationRequest(
    @SerializedName("email") val email: String
)

/** Body untuk `PATCH /api/v1/users/fcm-token`. */
data class FcmTokenRequest(
    @SerializedName("fcmToken") val fcmToken: String
)

/**
 * Representasi user dari backend. Berbeda dari [com.pkmk.bravy.data.model.User]
 * yang merupakan model Firebase RTDB, sehingga keduanya dipisahkan.
 */
data class BackendUser(
    @SerializedName("id") val id: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("isEmailVerified") val isEmailVerified: Boolean
)
