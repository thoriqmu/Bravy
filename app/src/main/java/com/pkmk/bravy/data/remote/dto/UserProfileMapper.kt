package com.pkmk.bravy.data.remote.dto

import com.pkmk.bravy.data.model.DailyMissionStatus
import com.pkmk.bravy.data.model.Friend
import com.pkmk.bravy.data.model.User
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Memetakan dokumen user backend ke model domain [User] yang dipakai UI.
 *
 * Pemetaan field yang berbeda nama: `id` → `uid`, `fullName` → `name`,
 * `avatarUrl` → `image`. Field Firebase yang tidak punya padanan di backend
 * (progres belajar, status misi, cap waktu percakapan) dibiarkan pada nilai
 * bawaannya karena belum dimigrasikan.
 */
fun UserProfileDto.toUser(): User {
    return User(
        uid = id.orEmpty(),
        name = fullName?.takeIf { it.isNotBlank() } ?: name.orEmpty(),
        email = email,
        redeemCode = redeemCode,
        bio = bio,
        image = avatarUrl,
        friends = friends?.associate { it.uid to Friend(status = it.status) },
        streak = streak,
        points = points,
        createdAt = parseIsoTimestamp(createdAt),
        dailyMissionStatus = DailyMissionStatus()
    )
}

/**
 * Memetakan hasil `GET /users/search` ke model domain [User].
 *
 * Dipakai untuk melengkapi entri teman (yang hanya berisi uid + status) menjadi
 * profil yang bisa ditampilkan, dan untuk daftar saran teman.
 */
fun BackendUserSummary.toUser(): User {
    return User(
        uid = id.orEmpty(),
        name = fullName?.takeIf { it.isNotBlank() } ?: name.orEmpty(),
        email = email,
        bio = bio,
        image = avatarUrl,
        streak = streak,
        points = points,
        createdAt = parseIsoTimestamp(createdAt)
    )
}

/**
 * Backend mengirim tanggal sebagai ISO-8601 UTC (mis. `2026-09-16T12:11:34.383Z`).
 * Nilai yang tidak terbaca menghasilkan 0 agar UI menampilkan "belum ada data"
 * alih-alih tanggal yang salah.
 */
internal fun parseIsoTimestamp(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching {
        val parser = SimpleDateFormat(ISO_PATTERN, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        parser.parse(value)?.time ?: 0L
    }.getOrDefault(0L)
}

private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
