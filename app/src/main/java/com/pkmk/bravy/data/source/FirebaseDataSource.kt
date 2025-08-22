package com.pkmk.bravy.data.source

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import com.google.firebase.storage.FirebaseStorage
import com.pkmk.bravy.data.model.CommunityPost
import com.pkmk.bravy.data.model.DailyMood
import com.pkmk.bravy.data.model.Friend
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.RedeemCode
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.util.Constants
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject

class FirebaseDataSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val storage: FirebaseStorage
) {
    private val redeemCodesRef = database.getReference(Constants.REDEEM_CODES_PATH)
    private val usersRef = database.getReference(Constants.USERS_PATH)
    private val privateChatsRef = database.getReference("private_chats")
    private val storageRef = storage.getReference("picture")
    private val chatMediaRef = storage.getReference("chat_media")
    private val communityChatsRef = database.getReference("community_chats")

    private val TAG = "FirebaseDataSource"

    suspend fun validateRedeemCode(code: String): RedeemCode? {
        return try {
            val snapshot = redeemCodesRef.child(code).get().await()
            Log.d(TAG, "Raw snapshot for redeem code $code: ${snapshot.value}")
            if (!snapshot.exists()) {
                Log.e(TAG, "Redeem code $code does not exist")
                return null
            }
            val isUsed = snapshot.child("isUsed").getValue(Boolean::class.java) ?: false
            val createdAt = snapshot.child("createdAt").getValue(String::class.java) ?: ""
            val redeemCode = RedeemCode(code = code, isUsed = isUsed, createdAt = createdAt)
            Log.d(TAG, "Deserialized redeem code $code: $redeemCode")
            redeemCode
        } catch (e: Exception) {
            Log.e(TAG, "Error validating redeem code $code: ${e.message}")
            null
        }
    }

    suspend fun markRedeemCodeAsUsed(code: String) {
        try {
            redeemCodesRef.child(code).child("isUsed").setValue(true).await()
            Log.d(TAG, "Marked redeem code $code as used")
        } catch (e: Exception) {
            Log.e(TAG, "Error marking redeem code $code as used: ${e.message}")
        }
    }

    suspend fun registerUser(user: User) {
        try {
            usersRef.child(user.uid).setValue(user).await()
            Log.d(TAG, "Registered user ${user.uid}")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering user: ${e.message}")
            throw e
        }
    }

    suspend fun createUserWithEmail(email: String, password: String): String? {
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Created user with email $email, uid: ${result.user?.uid}")
            return result.user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "Error creating user with email $email: ${e.message}")
            throw e
        }
    }

    suspend fun loginUser(email: String, password: String): String? {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Log.d(TAG, "Logged in user with email $email, uid: ${result.user?.uid}")
            return result.user?.uid
        } catch (e: Exception) {
            Log.e(TAG, "Error logging in user with email $email: ${e.message}")
            throw e
        }
    }

    suspend fun getUser(uid: String): User? {
        try {
            val snapshot = usersRef.child(uid).get().await()
            val user = snapshot.getValue(User::class.java)
            Log.d(TAG, "Fetched user $uid: $user")
            return user
        } catch (exception: Exception) {
            Log.e(TAG, "Error fetching user $uid: ${exception.message}")
            return null
        }
    }

    suspend fun updateUser(user: User) {
        try {
            usersRef.child(user.uid).setValue(user).await()
            Log.d(TAG, "Updated user ${user.uid}")
        } catch (exception: Exception) {
            Log.e(TAG, "Error updating user ${user.uid}: ${exception.message}")
            throw exception
        }
    }

    suspend fun uploadProfilePicture(imageFile: File, imageName: String): String {
        try {
            val fileRef = storageRef.child(imageName)
            fileRef.putFile(android.net.Uri.fromFile(imageFile)).await()
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.d(TAG, "Uploaded profile picture $imageName: $downloadUrl")
            return downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading profile picture $imageName: ${e.message}")
            throw e
        }
    }

    suspend fun createChatRoomIfNeeded(chatId: String, currentUser: User, otherUser: User) {
        val chatRef = privateChatsRef.child(chatId)
        val snapshot = chatRef.get().await()

        if (!snapshot.exists()) {
            // Jika chat room belum ada, buat sekarang dengan multi-path update
            try {
                // 1. Siapkan data yang akan ditulis
                val participants = mapOf(
                    currentUser.uid to mapOf("joined" to true, "name" to currentUser.name, "image" to currentUser.image),
                    otherUser.uid to mapOf("joined" to true, "name" to otherUser.name, "image" to otherUser.image)
                )

                // 2. Siapkan semua path yang akan diupdate dalam satu Map
                val updates = mutableMapOf<String, Any>()
                updates["/private_chats/$chatId/participants"] = participants
                updates["/users/${currentUser.uid}/chats/$chatId"] = true
                updates["/users/${otherUser.uid}/chats/$chatId"] = true

                // 3. Jalankan satu operasi updateChildren dari root reference
                database.reference.updateChildren(updates).await()
                Log.d(TAG, "Successfully created chat room atomically for chatId: $chatId")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating chat room atomically: ${e.message}")
                throw e // Lemparkan lagi error agar ViewModel tahu ada masalah
            }
        }
    }

    suspend fun startPrivateChat(user1Uid: String, user2Uid: String): String {
        try {
            val chatId = generateChatId(user1Uid, user2Uid)
            val user1 = getUser(user1Uid)
            val user2 = getUser(user2Uid)
            if (user1 == null || user2 == null) {
                throw Exception("User data not found")
            }
            val participants = mapOf(
                user1Uid to mapOf("joined" to true, "name" to user1.name, "image" to user1.image),
                user2Uid to mapOf("joined" to true, "name" to user2.name, "image" to user2.image)
            )
            privateChatsRef.child(chatId).child("participants").setValue(participants).await()
            usersRef.child(user1Uid).child("chats").child(chatId).setValue(true).await()
            usersRef.child(user2Uid).child("chats").child(chatId).setValue(true).await()
            Log.d(TAG, "Started private chat $chatId between $user1Uid and $user2Uid")
            return chatId
        } catch (e: Exception) {
            Log.e(TAG, "Error starting private chat: ${e.message}")
            throw e
        }
    }

    suspend fun sendMessage(chatId: String, message: Message) {
        try {
            val messageRef = privateChatsRef.child(chatId).child("messages").push()
            messageRef.setValue(message).await()
            Log.d(TAG, "Sent message ${messageRef.key} in chat $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message in chat $chatId: ${e.message}")
            throw e
        }
    }

    suspend fun uploadChatMedia(mediaFile: File, mediaName: String): String {
        try {
            val fileRef = chatMediaRef.child(mediaName)
            fileRef.putFile(android.net.Uri.fromFile(mediaFile)).await()
            val downloadUrl = fileRef.downloadUrl.await().toString()
            Log.d(TAG, "Uploaded chat media $mediaName: $downloadUrl")
            return downloadUrl
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading chat media $mediaName: ${e.message}")
            throw e
        }
    }

    suspend fun getChatMessages(chatId: String): List<Message> {
        try {
            val snapshot = privateChatsRef.child(chatId).child("messages").get().await()
            val messages = snapshot.children.mapNotNull { it.getValue(Message::class.java)?.copy(messageId = it.key ?: "") }
            Log.d(TAG, "Fetched ${messages.size} messages for chat $chatId")
            return messages
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages for chat $chatId: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getParticipantUids(chatId: String): List<String> {
        try {
            val snapshot = privateChatsRef.child(chatId).child("participants").get().await()
            val participantUids = snapshot.children.mapNotNull { it.key }
            Log.d(TAG, "Fetched ${participantUids.size} participant UIDs for chat $chatId")
            return participantUids
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching participant UIDs for chat $chatId: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getLastChatMessage(chatId: String): Message? {
        try {
            val snapshot = privateChatsRef.child(chatId).child("messages")
                .orderByChild("timestamp")
                .limitToLast(1)
                .get()
                .await()
            val message = snapshot.children.firstOrNull()?.getValue(Message::class.java)?.copy(messageId = snapshot.children.firstOrNull()?.key ?: "")
            Log.d(TAG, "Fetched last message for chat $chatId: $message")
            return message
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching last message for chat $chatId: ${e.message}")
            return null
        }
    }

    suspend fun getLastMessageTimestamp(chatId: String): Long? {
        try {
            val snapshot = privateChatsRef.child(chatId).child("messages")
                .orderByChild("timestamp")
                .limitToLast(1)
                .get()
                .await()
            val timestamp =
                snapshot.children.firstOrNull()?.child("timestamp")?.getValue(Long::class.java)
            Log.d(TAG, "Fetched last message timestamp for chat $chatId: $timestamp")
            return timestamp
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching last message timestamp for chat $chatId: ${e.message}")
            return null
        }
    }

    suspend fun getUserChats(userUid: String): List<String> {
        try {
            val snapshot = usersRef.child(userUid).child("chats").get().await()
            val chatIds = snapshot.children.mapNotNull { it.key }
            Log.d(TAG, "Fetched ${chatIds.size} chats for user $userUid")
            return chatIds
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching chats for user $userUid: ${e.message}")
            return emptyList()
        }
    }

    suspend fun getChatParticipant(chatId: String, currentUserUid: String): User? {
        try {
            val snapshot = privateChatsRef.child(chatId).child("participants").get().await()
            val otherUserUid = snapshot.children
                .mapNotNull { it.key }
                .find { it != currentUserUid }
            if (otherUserUid != null) {
                val participantSnapshot = snapshot.child(otherUserUid)
                val name = participantSnapshot.child("name").getValue(String::class.java) ?: ""
                val image = participantSnapshot.child("image").getValue(String::class.java) ?: ""
                return User(uid = otherUserUid, name = name, image = image)
            }
            Log.d(TAG, "No other participant found in chat $chatId")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching participant for chat $chatId: ${e.message}")
            return null
        }
    }

    private fun generateChatId(user1Uid: String, user2Uid: String): String {
        return if (user1Uid < user2Uid) "${user1Uid}_$user2Uid" else "${user2Uid}_$user1Uid"
    }

    suspend fun getLearningLevels(): DataSnapshot {
        return try {
            database.getReference("learning_levels").get().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching learning levels: ${e.message}")
            throw e
        }
    }

    // Fungsi untuk mendapatkan semua pengguna (untuk sugesti)
    suspend fun getAllUsers(): DataSnapshot {
        return usersRef.get().await()
    }

    // Fungsi untuk mengirim permintaan pertemanan
    suspend fun sendFriendRequest(fromUid: String, toUid: String) {
        // Set status 'sent' untuk pengirim
        usersRef.child(fromUid).child("friends").child(toUid).setValue(Friend(status = "sent")).await()
        // Set status 'received' untuk penerima
        usersRef.child(toUid).child("friends").child(fromUid).setValue(Friend(status = "received")).await()
    }

    // Fungsi untuk membatalkan/menolak permintaan
    suspend fun removeFriendship(uid1: String, uid2: String) {
        usersRef.child(uid1).child("friends").child(uid2).removeValue().await()
        usersRef.child(uid2).child("friends").child(uid1).removeValue().await()
    }

    // Fungsi untuk menerima permintaan pertemanan
    suspend fun acceptFriendRequest(accepterUid: String, senderUid: String) {
        // Set status 'friend' untuk keduanya
        usersRef.child(accepterUid).child("friends").child(senderUid).setValue(Friend(status = "friend")).await()
        usersRef.child(senderUid).child("friends").child(accepterUid).setValue(Friend(status = "friend")).await()
    }

    suspend fun getUserFriends(uid: String): DataSnapshot {
        return usersRef.child(uid).child("friends").get().await()
    }

    // Fungsi untuk membuat post baru (atomik)
    suspend fun createCommunityPost(post: CommunityPost) {
        val postId = post.postId
        // Multi-path update untuk menambahkan post dan mereferensikannya di data user
        val updates = mutableMapOf<String, Any>()
        updates["/community_chats/$postId"] = post
        updates["/users/${post.authorUid}/communities/$postId"] = true

        database.reference.updateChildren(updates).await()
    }

    // Fungsi untuk mendapatkan semua community post
    suspend fun getAllCommunityPosts(): List<CommunityPost> {
        val snapshot = communityChatsRef.orderByChild("timestamp").get().await()
        // --- TAMBAHKAN LOG ---
        Log.d("DataSource", "Found ${snapshot.childrenCount} community posts in total.")
        val posts = snapshot.children.mapNotNull { it.getValue(CommunityPost::class.java) }
        Log.d("DataSource", "Successfully deserialized ${posts.size} posts.")
        return posts.reversed()
    }

    suspend fun updateUserStreakAndMood(uid: String, newStreak: Int, newMood: DailyMood) {
        val updates = mapOf(
            "/users/$uid/streak" to newStreak,
            "/users/$uid/dailyMood" to newMood
        )
        database.reference.updateChildren(updates).await()
    }

}