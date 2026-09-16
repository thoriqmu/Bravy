package com.pkmk.bravy.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Profil user dari `GET/PATCH /api/v1/users/profile`.
 *
 * Berbeda dari [BackendUser] yang berasal dari `auth/me` dan hanya memuat field
 * ringkas, payload ini mengembalikan dokumen user apa adanya sehingga sebagian
 * besar field ditandai nullable agar perubahan di backend tidak mematahkan
 * parsing.
 */
data class UserProfileDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("redeemCode") val redeemCode: String? = null,
    @SerializedName("streak") val streak: Int = 0,
    @SerializedName("points") val points: Int = 0,
    @SerializedName("isEmailVerified") val isEmailVerified: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("friends") val friends: List<BackendFriend>? = null
)

/**
 * Entri daftar teman pada dokumen user; `status` = "friend", "sent", atau "received".
 *
 * Backend mengirim tepat tiga field ini pada `GET /users/friends`, bukan dokumen
 * user lengkap, sehingga detail nama/avatar harus diambil terpisah (lihat
 * [BackendUserSummary]).
 */
data class BackendFriend(
    @SerializedName("uid") val uid: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("createdAt") val createdAt: String? = null
)

/**
 * Satu baris hasil `GET /users/search` sebagai sumber data untuk melengkapi
 * [BackendFriend] menjadi profil lengkap di sisi klien.
 */
data class BackendUserSummary(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("fullName") val fullName: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("avatarUrl") val avatarUrl: String? = null,
    @SerializedName("streak") val streak: Int = 0,
    @SerializedName("points") val points: Int = 0,
    @SerializedName("createdAt") val createdAt: String? = null
)

/** Body untuk `PATCH /api/v1/users/profile`; field null tidak dikirim. */
data class UpdateProfileRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("bio") val bio: String? = null
)

/** `data` pada respons `POST /api/v1/users/avatar`: `{ "avatarUrl": "..." }`. */
data class AvatarResponse(
    @SerializedName("avatarUrl") val avatarUrl: String? = null
)

/** Body untuk `POST /api/v1/users/friends/request`. */
data class SendFriendRequestBody(
    @SerializedName("friendId") val friendId: String
)

/** Body untuk `PATCH /api/v1/users/friends/respond`. `action` = "accept" | "reject". */
data class RespondFriendRequestBody(
    @SerializedName("friendId") val friendId: String,
    @SerializedName("action") val action: String
)

/** Body untuk `POST /api/v1/auth/refresh-token`. */
data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

/** `data` pada respons refresh token; backend merotasi kedua token. */
data class RefreshTokenResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String
)
