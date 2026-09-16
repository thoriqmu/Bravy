package com.pkmk.bravy.data.repository

import android.util.Log
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
import com.pkmk.bravy.data.remote.BravyApiService
import com.pkmk.bravy.data.remote.TokenStore
import com.pkmk.bravy.data.remote.dto.BackendUser
import com.pkmk.bravy.data.remote.dto.BackendUserSummary
import com.pkmk.bravy.data.remote.dto.FcmTokenRequest
import com.pkmk.bravy.data.remote.dto.LoginRequest
import com.pkmk.bravy.data.remote.dto.RegisterRequest
import com.pkmk.bravy.data.remote.dto.ResendVerificationRequest
import com.pkmk.bravy.data.remote.dto.RespondFriendRequestBody
import com.pkmk.bravy.data.remote.dto.SendFriendRequestBody
import com.pkmk.bravy.data.remote.dto.UpdateProfileRequest
import com.pkmk.bravy.data.remote.dto.VerifyEmailRequest
import com.pkmk.bravy.data.remote.dto.toUser
import com.pkmk.bravy.data.remote.toApiFailure
import com.pkmk.bravy.data.remote.toResult
import com.pkmk.bravy.data.remote.toUnitResult
import com.pkmk.bravy.data.source.FirebaseDataSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseDataSource,
    private val apiService: BravyApiService,
    private val tokenStore: TokenStore
) : AuthRepository {
    private val TAG = "AuthRepositoryImpl"

    override suspend fun registerViaBackend(
        fullName: String,
        username: String,
        email: String,
        password: String
    ): Result<BackendUser> {
        return try {
            val response = apiService.register(
                RegisterRequest(
                    fullName = fullName,
                    username = username,
                    email = email,
                    password = password
                )
            )
            val result = response.data?.let { Result.success(it.user) }
                ?: Result.failure(Exception(response.message ?: "Registration failed"))
            result.onSuccess { Log.d(TAG, "Registered via backend: ${it.username}") }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering via backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun verifyEmail(token: String): Result<Unit> {
        return try {
            apiService.verifyEmail(VerifyEmailRequest(token)).toUnitResult()
                .onSuccess { Log.d(TAG, "Email verified via backend") }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying email: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun resendVerification(email: String): Result<Unit> {
        return try {
            apiService.resendVerification(ResendVerificationRequest(email)).toUnitResult()
                .onSuccess { Log.d(TAG, "Verification email resent to $email") }
        } catch (e: Exception) {
            Log.e(TAG, "Error resending verification to $email: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun loginViaBackend(identifier: String, password: String): Result<BackendUser> {
        return try {
            val response = apiService.login(LoginRequest(identifier, password))
            val data = response.data
                ?: return Result.failure(Exception(response.message ?: "Login failed"))

            tokenStore.saveTokens(data.accessToken, data.refreshToken)
            Log.d(TAG, "Logged in via backend: ${data.user.username}")
            Result.success(data.user)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in via backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun updateFcmToken(fcmToken: String): Result<Unit> {
        return try {
            apiService.updateFcmToken(FcmTokenRequest(fcmToken)).toUnitResult()
                .onSuccess { Log.d(TAG, "FCM token updated on backend") }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating FCM token: ${e.message}")
            e.toApiFailure()
        }
    }

    override fun hasActiveSession(): Boolean = tokenStore.isLoggedIn()

    override suspend fun getProfileBackend(): Result<User> {
        return try {
            val response = apiService.getProfile()
            if (!response.success) {
                return Result.failure(Exception(response.message ?: DEFAULT_PROFILE_ERROR))
            }
            val dto = response.data
                ?: return Result.failure(Exception(response.message ?: DEFAULT_PROFILE_ERROR))
            Result.success(dto.toUser())
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile from backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun updateProfileBackend(name: String, bio: String): Result<User> {
        return try {
            val response = apiService.updateProfile(UpdateProfileRequest(name = name, bio = bio))
            if (!response.success) {
                return Result.failure(Exception(response.message ?: DEFAULT_PROFILE_ERROR))
            }
            val dto = response.data
                ?: return Result.failure(Exception(response.message ?: DEFAULT_PROFILE_ERROR))
            Log.d(TAG, "Profile updated on backend")
            Result.success(dto.toUser())
        } catch (e: Exception) {
            Log.e(TAG, "Error updating profile on backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun uploadAvatarBackend(imageFile: File): Result<String> {
        return try {
            val part = MultipartBody.Part.createFormData(
                "avatar",
                imageFile.name,
                imageFile.asRequestBody(AVATAR_MEDIA_TYPE)
            )
            val response = apiService.uploadAvatar(part)
            val avatarUrl = response.data?.avatarUrl
            if (!response.success || avatarUrl.isNullOrBlank()) {
                return Result.failure(Exception(response.message ?: "Gagal mengunggah foto profil."))
            }
            Log.d(TAG, "Avatar uploaded to backend")
            Result.success(avatarUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading avatar to backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun getFriendsBackend(): Result<List<FriendInfo>> {
        return try {
            val response = apiService.getFriends()
            if (!response.success) {
                return Result.failure(Exception(response.message ?: "Gagal memuat daftar teman."))
            }
            val friends = response.data.orEmpty()
            // Backend hanya mengirim uid + status pada endpoint ini, sehingga profil
            // dilengkapi dari hasil pencarian. Bila pelengkapan gagal, entri tetap
            // ditampilkan dengan data minimal agar daftar teman tidak kosong.
            val profiles = searchUserSummaries()
            val friendInfoList = friends.map { friend ->
                val profile = profiles[friend.uid]
                FriendInfo(
                    user = profile?.toUser() ?: User(uid = friend.uid),
                    status = friend.status
                )
            }
            Result.success(friendInfoList)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching friends from backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun searchUsersBackend(query: String, limit: Int): Result<List<User>> {
        return try {
            val response = apiService.searchUsers(query.ifBlank { MATCH_ALL_QUERY })
            if (!response.success) {
                return Result.failure(Exception(response.message ?: "Gagal mencari pengguna."))
            }
            Result.success(response.data.orEmpty().take(limit).map { it.toUser() })
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users on backend: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun sendFriendRequestBackend(friendId: String): Result<Unit> {
        return try {
            apiService.sendFriendRequest(SendFriendRequestBody(friendId)).toUnitResult()
                .onSuccess { Log.d(TAG, "Friend request sent to $friendId") }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending friend request to $friendId: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun respondFriendRequestBackend(friendId: String, action: String): Result<Unit> {
        return try {
            apiService.respondFriendRequest(RespondFriendRequestBody(friendId, action)).toUnitResult()
                .onSuccess { Log.d(TAG, "Friend request from $friendId $action") }
        } catch (e: Exception) {
            Log.e(TAG, "Error responding to friend request from $friendId: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun removeFriendBackend(friendId: String): Result<Unit> {
        // Backend tidak punya DELETE friends/{id}; action=reject melakukan $pull
        // pada kedua dokumen user sehingga juga menghapus pertemanan yang sudah aktif.
        return respondFriendRequestBackend(friendId, ACTION_REJECT)
    }

    override suspend fun logoutBackend(): Result<Unit> {
        val remoteResult = try {
            apiService.logout().toUnitResult()
        } catch (e: Exception) {
            Log.w(TAG, "Logout backend gagal, token lokal tetap dihapus: ${e.message}")
            e.toApiFailure<Unit>()
        }
        // Token lokal selalu dibersihkan agar pengguna tidak terjebak dalam sesi
        // yang tidak bisa keluar saat server tidak dapat dijangkau.
        tokenStore.clear()
        return remoteResult
    }

    /**
     * Mengambil seluruh user selain diri sendiri sebagai peta `uid` → ringkasan profil.
     * Kegagalan diperlakukan sebagai peta kosong agar pemanggil tetap dapat
     * menampilkan data minimal.
     */
    private suspend fun searchUserSummaries(): Map<String, BackendUserSummary> {
        return try {
            val response = apiService.searchUsers(MATCH_ALL_QUERY)
            response.data.orEmpty()
                .mapNotNull { summary -> summary.id?.let { it to summary } }
                .toMap()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal melengkapi profil teman dari pencarian: ${e.message}")
            emptyMap()
        }
    }


    override suspend fun validateRedeemCode(code: String): Result<RedeemCode> {
        return try {
            val redeemCode = dataSource.validateRedeemCode(code)
            Log.d(TAG, "Validating redeem code $code: $redeemCode")
            when {
                redeemCode == null -> {
                    Log.e(TAG, "Invalid redeem code: $code")
                    Result.failure(Exception("Invalid redeem code"))
                }
                redeemCode.isUsed -> {
                    Log.e(TAG, "Redeem code $code has been used")
                    Result.failure(Exception("Redeem code has been used"))
                }
                else -> {
                    Log.d(TAG, "Redeem code $code is valid")
                    Result.success(redeemCode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating redeem code $code: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun markRedeemCodeAsUsed(code: String): Result<Unit> {
        return try {
            dataSource.markRedeemCodeAsUsed(code)
            Log.d(TAG, "Successfully marked redeem code $code as used")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking redeem code $code as used: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun registerUser(user: User): Result<Unit> {
        return try {
            dataSource.registerUser(user)
            Log.d(TAG, "Successfully registered user ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user ${user.uid}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun createUserWithEmail(email: String, password: String): Result<String> {
        return try {
            val uid = dataSource.createUserWithEmail(email, password)
            if (uid != null) {
                Log.d(TAG, "Successfully created user with email $email, uid: $uid")
                Result.success(uid)
            } else {
                Log.e(TAG, "Failed to create user with email $email: UID is null")
                Result.failure(Exception("Failed to create user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user with email $email: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun loginUser(email: String, password: String): Result<String> {
        return try {
            val uid = dataSource.loginUser(email, password)
            if (uid != null) {
                Log.d(TAG, "Successfully logged in user with email $email, uid: $uid")
                Result.success(uid)
            } else {
                Log.e(TAG, "Failed to login user with email $email: UID is null")
                Result.failure(Exception("Login failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in user with email $email: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun saveSessionData(token: String, sessionId: String): Result<Unit> {
        return try {
            val uid = dataSource.getCurrentUserId()
            if (uid != null) {
                dataSource.saveSessionData(uid, token, sessionId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("User not logged in"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUser(uid: String): Result<User> {
        return try {
            val user = dataSource.getUser(uid)
            if (user != null) {
                Log.d(TAG, "Successfully fetched user $uid")
                Result.success(user)
            } else {
                Log.e(TAG, "User $uid not found")
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user $uid: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getLearningLevels(): Result<DataSnapshot> {
        return try {
            val snapshot = dataSource.getLearningLevels()
            Result.success(snapshot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startPrivateChat(user1Uid: String, user2Uid: String): Result<String> {
        return try {
            val chatId = dataSource.startPrivateChat(user1Uid, user2Uid)
            Result.success(chatId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadChatImage(imageBytes: ByteArray): Result<String> {
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val downloadUrl = dataSource.uploadChatImage(imageBytes, fileName)
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadChatAudio(audioFile: File): Result<String> {
        return try {
            val fileName = "${UUID.randomUUID()}.3gp"
            val downloadUrl = dataSource.uploadChatAudio(audioFile, fileName)
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createChatRoomIfNeeded(chatId: String, currentUser: User, otherUser: User): Result<Unit> {
        return try {
            dataSource.createChatRoomIfNeeded(chatId, currentUser, otherUser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createCommunityPost(post: CommunityPost): Result<Unit> {
        return try {
            dataSource.createCommunityPost(post)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadCommunityPostImage(imageBytes: ByteArray): Result<String> {
        return try {
            val fileName = "${UUID.randomUUID()}.jpg"
            val downloadUrl = dataSource.uploadCommunityPostImage(imageBytes, fileName)
            Result.success(downloadUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAllCommunityPostsWithDetails(): Result<List<CommunityPostDetails>> {
        return try {
            val posts = dataSource.getAllCommunityPosts()
            Log.d("Repository", "getAllCommunityPosts returned ${posts.size} posts.")
            val postDetailsList = mutableListOf<CommunityPostDetails>()

            for (post in posts) {
                val authorResult = getUser(post.authorUid)
                authorResult.onSuccess { author ->
                    postDetailsList.add(CommunityPostDetails(post, author))
                    // --- TAMBAHKAN LOG ---
                    Log.d("Repository", "Successfully fetched details for author ${author.uid}")
                }.onFailure { exception ->
                    // --- TAMBAHKAN LOG ---
                    Log.e("Repository", "Failed to get user details for author Uid: ${post.authorUid}. Skipping post. Error: ${exception.message}")
                }
            }
            Log.d("Repository", "Returning ${postDetailsList.size} posts with details.")
            Result.success(postDetailsList)
        } catch (e: Exception) {
            Log.e("Repository", "Error in getAllCommunityPostsWithDetails: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun updateUserStreakAndMood(uid: String, newStreak: Int, newMood: DailyMood): Result<Unit> {
        return try {
            dataSource.updateUserStreakAndMood(uid, newStreak, newMood)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun toggleLikeOnPost(postId: String, uid: String): Result<Unit> {
        return try {
            dataSource.toggleLikeOnPost(postId, uid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun postComment(postId: String, comment: Comment): Result<Unit> {
        return try {
            dataSource.postComment(postId, comment)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun listenForLatestCommunityPost(callback: (Result<CommunityPost?>) -> Unit) {
        dataSource.listenForLatestCommunityPost(callback)
    }

    override fun removeLatestPostListener() {
        dataSource.removeLatestPostListener()
    }

    override fun listenForUserChats(uid: String, onChatsUpdated: () -> Unit) {
        dataSource.listenForUserChats(uid, onChatsUpdated)
    }

    override fun removeUserChatsListener() {
        dataSource.removeUserChatsListener()
    }

    override suspend fun getUserNotifications(): Result<List<AppNotification>> {
        return try {
            val uid = dataSource.getCurrentUserId()
            if (uid != null) {
                val notifications = dataSource.getUserNotifications(uid)
                Result.success(notifications)
            } else {
                Log.e(TAG, "Cannot get notifications: User is not logged in.")
                Result.failure(Exception("User not logged in"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user notifications", e)
            Result.failure(e)
        }
    }

    override suspend fun getDailyMissionTopics(): Result<List<String>> {
        return try {
            Result.success(dataSource.getDailyMissionTopics())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserMissionsAndStreak(uid: String, status: DailyMissionStatus, emotion: String, timestamp: Long, streak: Int, confidence: Int, wordCount: Int): Result<Unit> {
        return try {
            dataSource.updateUserMissionsAndStreak(uid, status, emotion, timestamp, streak, confidence, wordCount)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun completeDailyMission(missionType: MissionType): Result<Unit> {
        val uid = dataSource.getCurrentUserId() ?: return Result.failure(Exception("User not logged in"))

        return try {
            val userResult = getUser(uid)
            if (userResult.isFailure) return Result.failure(userResult.exceptionOrNull()!!)

            val user = userResult.getOrThrow()
            val todayDateString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            val currentStatus = user.dailyMissionStatus?.takeIf { it.date == todayDateString }
                ?: DailyMissionStatus(date = todayDateString)

            val missionKey = missionType.name
            val timestampField: String
            val lastActionTimestamp: Long

            when (missionType) {
                MissionType.COMMUNITY -> {
                    timestampField = "lastCommunityInteractionTimestamp"
                    lastActionTimestamp = user.lastCommunityInteractionTimestamp
                }
                MissionType.CHAT -> {
                    timestampField = "lastPrivateChatTimestamp"
                    lastActionTimestamp = user.lastPrivateChatTimestamp
                }
                else -> return Result.success(Unit) // Abaikan untuk tipe speaking
            }

            // Cek apakah sudah diselesaikan hari ini
            if (currentStatus.completedMissions[missionKey] == true && isSameDay(System.currentTimeMillis(), lastActionTimestamp)) {
                return Result.success(Unit) // Sudah selesai, tidak perlu update
            }

            // Jika belum, update
            val updatedMissions = currentStatus.completedMissions.toMutableMap().apply { this[missionKey] = true }
            val newStatus = currentStatus.copy(completedMissions = updatedMissions)

            dataSource.completeMission(uid, missionKey, newStatus, timestampField, System.currentTimeMillis())
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val DEFAULT_PROFILE_ERROR = "Gagal memuat profil"
        private const val MATCH_ALL_QUERY = ""
        private const val ACTION_REJECT = "reject"
        private val AVATAR_MEDIA_TYPE = "image/*".toMediaType()
    }
}