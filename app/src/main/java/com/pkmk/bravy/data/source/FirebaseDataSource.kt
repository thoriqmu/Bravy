package com.pkmk.bravy.data.source

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.getValue
import com.google.firebase.storage.FirebaseStorage
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
}