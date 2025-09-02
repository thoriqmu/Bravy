package com.pkmk.bravy.data.repository

import com.google.firebase.database.DataSnapshot
import com.pkmk.bravy.data.model.Comment
import com.pkmk.bravy.data.model.CommunityPost
import com.pkmk.bravy.data.model.CommunityPostDetails
import com.pkmk.bravy.data.model.DailyMood
import com.pkmk.bravy.data.model.FriendInfo
import com.pkmk.bravy.data.model.RedeemCode
import com.pkmk.bravy.data.model.User
import java.io.File

interface AuthRepository {
    suspend fun validateRedeemCode(code: String): Result<RedeemCode>
    suspend fun markRedeemCodeAsUsed(code: String): Result<Unit>
    suspend fun registerUser(user: User): Result<Unit>
    suspend fun createUserWithEmail(email: String, password: String): Result<String>
    suspend fun loginUser(email: String, password: String): Result<String>
    suspend fun saveSessionData(token: String, sessionId: String): Result<Unit>
    suspend fun getUser(uid: String): Result<User>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun uploadProfilePicture(uid: String, imageFile: File): Result<String>
    suspend fun getLearningLevels(): Result<DataSnapshot>
    suspend fun getSuggestedFriends(currentUid: String, limit: Int): Result<List<User>>
    suspend fun sendFriendRequest(fromUid: String, toUid: String): Result<Unit>
    suspend fun cancelFriendRequest(fromUid: String, toUid: String): Result<Unit>
    suspend fun getFriendsData(currentUid: String): Result<List<FriendInfo>>
    suspend fun acceptFriendRequest(accepterUid: String, senderUid: String): Result<Unit>
    suspend fun removeFriendship(uid1: String, uid2: String): Result<Unit>
    suspend fun startPrivateChat(user1Uid: String, user2Uid: String): Result<String>
    suspend fun createChatRoomIfNeeded(chatId: String, currentUser: User, otherUser: User): Result<Unit>
    suspend fun createCommunityPost(post: CommunityPost): Result<Unit>
    suspend fun getAllCommunityPostsWithDetails(): Result<List<CommunityPostDetails>>
    suspend fun updateUserStreakAndMood(uid: String, newStreak: Int, newMood: DailyMood): Result<Unit>
    suspend fun toggleLikeOnPost(postId: String, uid: String): Result<Unit>
    suspend fun postComment(postId: String, comment: Comment): Result<Unit>
    fun listenForLatestCommunityPost(callback: (Result<CommunityPost?>) -> Unit)
    fun removeLatestPostListener()
    fun listenForUserChats(uid: String, onChatsUpdated: () -> Unit)
    fun removeUserChatsListener()
}