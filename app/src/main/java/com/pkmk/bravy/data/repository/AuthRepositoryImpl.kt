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
import com.pkmk.bravy.data.source.FirebaseDataSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val dataSource: FirebaseDataSource
) : AuthRepository {
    private val TAG = "AuthRepositoryImpl"

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

    override suspend fun updateUser(user: User): Result<Unit> {
        return try {
            dataSource.updateUser(user)
            Log.d(TAG, "Successfully updated user ${user.uid}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user ${user.uid}: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun uploadProfilePicture(uid: String, imageFile: File): Result<String> {
        return try {
            val imageName = "profile_$uid.jpg"
            val downloadUrl = dataSource.uploadProfilePicture(imageFile, imageName)
            Log.d(TAG, "Successfully uploaded profile picture for $uid: $downloadUrl")
            Result.success(imageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading profile picture for $uid: ${e.message}")
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

    override suspend fun getSuggestedFriends(currentUid: String, limit: Int): Result<List<User>> {
        return try {
            val allUsersSnapshot = dataSource.getAllUsers()
            val allUsers = allUsersSnapshot.children.mapNotNull { it.getValue(User::class.java) }

            // Dapatkan data user saat ini
            val currentUser = allUsers.firstOrNull { it.uid == currentUid }

            // --- PERBAIKAN DI SINI ---
            // Cek apakah 'friends' null. Jika ya, gunakan set kosong.
            // Ini mencegah NullPointerException pada pengguna baru.
            val existingFriendIds = currentUser?.friends?.keys ?: emptySet()

            // Filter: bukan diri sendiri, dan belum ada di daftar teman (termasuk yang sudah dikirim permintaan)
            val suggested = allUsers.filter { user ->
                user.uid != currentUid && !existingFriendIds.contains(user.uid)
            }.shuffled().take(limit) // Ambil 3 secara acak

            Result.success(suggested)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting suggested friends: ${e.message}", e) // Tambahkan Log.e untuk debug
            Result.failure(e)
        }
    }

    override suspend fun sendFriendRequest(fromUid: String, toUid: String): Result<Unit> {
        return try {
            dataSource.sendFriendRequest(fromUid, toUid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelFriendRequest(fromUid: String, toUid: String): Result<Unit> {
        return try {
            dataSource.removeFriendship(fromUid, toUid) // Menggunakan fungsi yang sama untuk cancel/reject
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFriendsData(currentUid: String): Result<List<FriendInfo>> {
        return try {
            // 1. Ambil daftar ID teman dan statusnya dari pengguna saat ini
            val friendsSnapshot = dataSource.getUserFriends(currentUid)
            val friendIdStatusMap = friendsSnapshot.children.associate {
                it.key!! to (it.child("status").getValue(String::class.java) ?: "")
            }

            // 2. Ambil data lengkap untuk setiap teman berdasarkan ID
            val friendInfoList = mutableListOf<FriendInfo>()
            for ((friendId, status) in friendIdStatusMap) {
                val userResult = getUser(friendId) // Menggunakan kembali fungsi getUser yang sudah ada
                userResult.onSuccess { user ->
                    friendInfoList.add(FriendInfo(user, status))
                }
            }
            Result.success(friendInfoList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun acceptFriendRequest(accepterUid: String, senderUid: String): Result<Unit> {
        return try {
            dataSource.acceptFriendRequest(accepterUid, senderUid)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFriendship(uid1: String, uid2: String): Result<Unit> {
        return try {
            dataSource.removeFriendship(uid1, uid2)
            Result.success(Unit)
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
}