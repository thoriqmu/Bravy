package com.pkmk.bravy.data.repository

import com.google.firebase.database.DataSnapshot
import com.pkmk.bravy.data.model.AppNotification
import com.pkmk.bravy.data.model.Comment
import com.pkmk.bravy.data.model.CommunityPost
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.data.model.DailyMissionStatus
import com.pkmk.bravy.data.model.DailyMood
import com.pkmk.bravy.data.model.FriendInfo
import com.pkmk.bravy.data.model.MissionType
import com.pkmk.bravy.data.model.RedeemCode
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.remote.dto.BackendUser
import java.io.File

interface AuthRepository {
    // --- Backend REST (Bravy) ---
    suspend fun registerViaBackend(fullName: String, username: String, email: String, password: String): Result<BackendUser>
    suspend fun verifyEmail(token: String): Result<Unit>
    suspend fun resendVerification(email: String): Result<Unit>
    suspend fun loginViaBackend(identifier: String, password: String): Result<BackendUser>
    suspend fun updateFcmToken(fcmToken: String): Result<Unit>

    /** Profil user yang sedang login (`GET /users/profile`). */
    suspend fun getProfileBackend(): Result<User>

    /** Memperbarui nama/bio user yang sedang login (`PATCH /users/profile`). */
    suspend fun updateProfileBackend(name: String, bio: String): Result<User>

    /** Mengunggah avatar dan mengembalikan URL hasilnya (`POST /users/avatar`). */
    suspend fun uploadAvatarBackend(imageFile: File): Result<String>

    /** Daftar teman lengkap dengan profilnya (`GET /users/friends` + pelengkapan via search). */
    suspend fun getFriendsBackend(): Result<List<FriendInfo>>

    /**
     * Mencari user lain. [query] kosong berarti "semua user selain diri sendiri",
     * dipakai untuk daftar saran teman.
     */
    suspend fun searchUsersBackend(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): Result<List<User>>

    suspend fun sendFriendRequestBackend(friendId: String): Result<Unit>

    /** `action` = "accept" | "reject". */
    suspend fun respondFriendRequestBackend(friendId: String, action: String): Result<Unit>

    /**
     * Menghapus pertemanan atau menolak permintaan. Backend tidak menyediakan
     * `DELETE /users/friends/{id}`, sehingga penghapusan memakai
     * `PATCH /users/friends/respond` dengan `action=reject` yang melakukan
     * `$pull` pada kedua sisi dokumen user.
     */
    suspend fun removeFriendBackend(friendId: String): Result<Unit>

    /**
     * Mencabut refresh token di server (`POST /auth/logout`) lalu menghapus token
     * lokal. Kegagalan jaringan tetap menghapus token lokal agar pengguna tidak
     * terjebak dalam sesi yang tidak bisa keluar.
     */
    suspend fun logoutBackend(): Result<Unit>

    // --- Firebase (dipertahankan untuk fitur yang belum dimigrasikan) ---
    suspend fun validateRedeemCode(code: String): Result<RedeemCode>
    suspend fun markRedeemCodeAsUsed(code: String): Result<Unit>
    suspend fun registerUser(user: User): Result<Unit>
    suspend fun createUserWithEmail(email: String, password: String): Result<String>
    suspend fun loginUser(email: String, password: String): Result<String>
    suspend fun saveSessionData(token: String, sessionId: String): Result<Unit>
    suspend fun getUser(uid: String): Result<User>
    suspend fun getLearningLevels(): Result<DataSnapshot>
    suspend fun startPrivateChat(user1Uid: String, user2Uid: String): Result<String>
    suspend fun uploadChatImage(imageBytes: ByteArray): Result<String>
    suspend fun uploadChatAudio(audioFile: File): Result<String>
    suspend fun createChatRoomIfNeeded(chatId: String, currentUser: User, otherUser: User): Result<Unit>
    suspend fun createCommunityPost(post: CommunityPost): Result<Unit>
    suspend fun uploadCommunityPostImage(imageBytes: ByteArray): Result<String>
    suspend fun getAllCommunityPostsWithDetails(): Result<List<CommunityPostDetails>>
    suspend fun updateUserStreakAndMood(uid: String, newStreak: Int, newMood: DailyMood): Result<Unit>
    suspend fun toggleLikeOnPost(postId: String, uid: String): Result<Unit>
    suspend fun postComment(postId: String, comment: Comment): Result<Unit>
    fun listenForLatestCommunityPost(callback: (Result<CommunityPost?>) -> Unit)
    fun removeLatestPostListener()
    fun listenForUserChats(uid: String, onChatsUpdated: () -> Unit)
    fun removeUserChatsListener()
    suspend fun getUserNotifications(): Result<List<AppNotification>>
    suspend fun getDailyMissionTopics(): Result<List<String>>
    suspend fun updateUserMissionsAndStreak(uid: String, status: DailyMissionStatus, emotion: String, timestamp: Long, streak: Int, confidence: Int, wordCount: Int): Result<Unit>
    suspend fun completeDailyMission(missionType: MissionType): Result<Unit>

    companion object {
        /** Batas jumlah user yang dipakai untuk melengkapi profil teman & saran. */
        const val DEFAULT_SEARCH_LIMIT = 50
    }
}