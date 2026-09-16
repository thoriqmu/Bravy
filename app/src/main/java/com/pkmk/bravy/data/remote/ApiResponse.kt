package com.pkmk.bravy.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.HttpException

/**
 * Bentuk respons standar dari backend Bravy:
 * `{ "success": true, "message": "...", "data": { ... } }`.
 *
 * [data] null untuk endpoint yang tidak mengembalikan payload (verify-email,
 * resend-verification, fcm-token).
 */
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("errors") val errors: List<ApiError>? = null
)

/** Detail error validasi per-field yang dikirim backend pada respons 400. */
data class ApiError(
    @SerializedName("field") val field: String? = null,
    @SerializedName("message") val message: String? = null
)

/**
 * Mengubah [ApiResponse] menjadi [Result], memakai [ApiResponse.message] sebagai
 * pesan kegagalan agar bisa langsung ditampilkan ke pengguna.
 */
fun <T> ApiResponse<T>.toResult(): Result<T> {
    if (!success) {
        return Result.failure(Exception(message ?: DEFAULT_ERROR_MESSAGE))
    }
    val payload = data
        ?: return Result.failure(Exception(message ?: DEFAULT_ERROR_MESSAGE))
    return Result.success(payload)
}

/**
 * Sama seperti [toResult] tetapi untuk endpoint yang memang tidak mengembalikan
 * payload, sehingga `data == null` tetap dianggap sukses.
 */
fun ApiResponse<Unit>.toUnitResult(): Result<Unit> {
    return if (success) {
        Result.success(Unit)
    } else {
        Result.failure(Exception(message ?: DEFAULT_ERROR_MESSAGE))
    }
}

/**
 * Menerjemahkan kegagalan jaringan/HTTP menjadi [Result.failure] dengan pesan
 * yang ramah pengguna. Pesan dari backend (`message`) diprioritaskan karena
 * biasanya sudah deskriptif, misalnya "Invalid email/username or password".
 */
fun <T> Throwable.toApiFailure(): Result<T> {
    val message = when (this) {
        is HttpException -> parseHttpErrorMessage(this)
        is java.net.UnknownHostException,
        is java.net.ConnectException,
        is java.net.SocketTimeoutException -> "Tidak dapat terhubung ke server. Periksa koneksi Anda."
        else -> message ?: DEFAULT_ERROR_MESSAGE
    }
    return Result.failure(Exception(message, this))
}

/**
 * Mencoba membaca pesan `message` dari body error backend. Body respons tidak
 * selalu dalam bentuk JSON yang diharapkan, jadi kegagalan parsing diabaikan
 * dan pengguna melihat pesan generik.
 */
private fun parseHttpErrorMessage(exception: HttpException): String {
    val fallback = when (exception.code()) {
        401 -> "Sesi Anda telah berakhir. Silakan masuk kembali."
        403 -> "Anda tidak memiliki akses ke tindakan ini."
        404 -> "Data yang diminta tidak ditemukan."
        in 500..599 -> "Terjadi kesalahan pada server. Coba lagi nanti."
        else -> "Permintaan gagal (${exception.code()})"
    }

    val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
    if (body.isNullOrBlank()) return fallback

    return runCatching {
        com.google.gson.Gson().fromJson(body, ApiResponse::class.java)
            ?.takeIf { it.success == false }
            ?.message
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
}

private const val DEFAULT_ERROR_MESSAGE = "Terjadi kesalahan. Silakan coba lagi."
