package com.pkmk.bravy.data.repository

import android.util.Log
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.remote.BravyApiService
import com.pkmk.bravy.data.remote.dto.ChatMessageDto
import com.pkmk.bravy.data.remote.dto.ConversationDto
import com.pkmk.bravy.data.remote.dto.ConversationWithUser
import com.pkmk.bravy.data.remote.dto.CreateConversationRequest
import com.pkmk.bravy.data.remote.dto.MEDIA_TYPE_TEXT
import com.pkmk.bravy.data.remote.dto.SendMessageRequest
import com.pkmk.bravy.data.remote.dto.parseIsoTimestamp
import com.pkmk.bravy.data.remote.dto.toUser
import com.pkmk.bravy.data.remote.toApiFailure
import com.pkmk.bravy.data.remote.toResult
import com.pkmk.bravy.data.remote.toUnitResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi REST dari [ChatRepository].
 *
 * Beberapa pemanggilan dijalankan paralel agar daftar percakapan tidak menunggu
 * dua perjalanan jaringan secara berurutan: percakapan dan profil lawan bicara
 * diambil bersamaan, begitu pula saat menentukan id pengguna sendiri.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val apiService: BravyApiService
) : ChatRepository {

    private val currentUserIdMutex = Mutex()

    /**
     * Id pengguna sendiri jarang berubah, jadi hasil `GET /auth/me` disimpan
     * agar pemetaan pesan tidak memicu request baru setiap kali dibutuhkan.
     */
    @Volatile
    private var cachedCurrentUserId: String? = null

    override suspend fun getConversations(): Result<List<RecentChat>> {
        return try {
            coroutineScope {
                // Ketiga request tidak saling bergantung, sehingga dijalankan bersamaan.
                val conversationsJob = async { apiService.getConversations() }
                val currentUserIdJob = async { getCurrentUserId() }
                val profilesJob = async { searchUserProfiles() }

                val response = conversationsJob.await()
                if (!response.success) {
                    return@coroutineScope Result.failure(
                        Exception(response.message ?: "Gagal memuat percakapan.")
                    )
                }

                val currentUserId = currentUserIdJob.await()
                val profiles = profilesJob.await()

                val conversations = response.data.orEmpty().mapNotNull { conversation ->
                    val otherUserId = conversation.otherParticipantId(currentUserId)
                        ?: return@mapNotNull null
                    // Backend hanya menyimpan uid pada percakapan, sehingga profil
                    // dilengkapi dari hasil pencarian. Bila tidak ditemukan, entri
                    // tetap ditampilkan dengan data minimal agar tidak hilang.
                    val otherUser = profiles[otherUserId] ?: User(uid = otherUserId)
                    conversation.toRecentChat(otherUser)
                }
                Result.success(conversations)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat percakapan: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun getConversation(conversationId: String): Result<ConversationWithUser> {
        return try {
            val response = apiService.getConversations()
            if (!response.success) {
                return Result.failure(Exception(response.message ?: "Gagal memuat percakapan."))
            }

            val conversation = response.data.orEmpty().firstOrNull { it.id == conversationId }
                ?: return Result.failure(Exception("Percakapan tidak ditemukan."))
            val otherUserId = conversation.otherParticipantId(getCurrentUserId())
                ?: return Result.failure(Exception("Percakapan tidak memiliki lawan bicara."))

            Result.success(
                ConversationWithUser(
                    conversation = conversation,
                    otherUser = getUserProfile(otherUserId) ?: User(uid = otherUserId)
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat percakapan $conversationId: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun getUserProfile(userId: String): User? {
        if (userId.isBlank()) return null
        return try {
            // `q` dicocokkan ke nama/email/username, dan hasilnya disaring ulang
            // berdasarkan id supaya pencarian yang meleset tidak salah dipakai.
            apiService.searchUsers(userId).data.orEmpty()
                .firstOrNull { it.id == userId }
                ?.toUser()
        } catch (e: Exception) {
            Log.w(TAG, "Gagal memuat profil $userId: ${e.message}")
            null
        }
    }

    override suspend fun createConversation(participantId: String): Result<ConversationDto> {
        return try {
            apiService.createConversation(CreateConversationRequest(participantId)).toResult()
                .onSuccess { Log.d(TAG, "Percakapan siap: ${it.id}") }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat percakapan dengan $participantId: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun getMessages(
        conversationId: String,
        page: Int
    ): Result<List<ChatMessageDto>> {
        return try {
            val response = apiService.getMessages(conversationId, page)
            if (!response.success) {
                return Result.failure(Exception(response.message ?: "Gagal memuat pesan."))
            }
            // Backend mengirim urutan terbaru lebih dulu untuk kebutuhan paginasi,
            // sedangkan UI menampilkan dari atas ke bawah menurut waktu.
            Result.success(response.data.orEmpty().reversed())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat pesan percakapan $conversationId: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun sendMessage(
        conversationId: String,
        text: String?,
        mediaType: String,
        mediaUrl: String?,
        clientMessageId: String?
    ): Result<ChatMessageDto> {
        return try {
            val request = SendMessageRequest(
                text = text,
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                // Id dibuat otomatis bila pemanggil tidak menyiapkannya lebih dulu
                // untuk pesan optimistis, sehingga percobaan ulang tetap idempoten.
                clientMessageId = clientMessageId ?: newClientMessageId()
            )
            apiService.sendMessage(conversationId, request).toResult()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengirim pesan ke $conversationId: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun markRead(conversationId: String): Result<Unit> {
        return try {
            apiService.markConversationRead(conversationId).toUnitResult()
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menandai percakapan $conversationId terbaca: ${e.message}")
            e.toApiFailure()
        }
    }

    override suspend fun getCurrentUserId(): String? {
        cachedCurrentUserId?.let { return it }

        return try {
            val id = apiService.getMe().data?.id
            if (!id.isNullOrBlank()) {
                currentUserIdMutex.withLock { cachedCurrentUserId = id }
            }
            id
        } catch (e: Exception) {
            // Tanpa id, pesan tetap bisa dikirim; hanya sisi UI (pesan sendiri vs
            // lawan) yang tidak dapat ditentukan, sehingga kegagalan tidak fatal.
            Log.w(TAG, "Gagal menentukan id pengguna: ${e.message}")
            null
        }
    }

    override fun newClientMessageId(): String = UUID.randomUUID().toString()

    /** Seluruh user selain diri sendiri sebagai peta `id` → profil. */
    private suspend fun searchUserProfiles(): Map<String, User> {
        return try {
            apiService.searchUsers(MATCH_ALL_QUERY).data.orEmpty()
                .mapNotNull { summary -> summary.id?.let { it to summary.toUser() } }
                .toMap()
        } catch (e: Exception) {
            // Daftar percakapan tetap ditampilkan dengan nama kosong daripada gagal.
            Log.w(TAG, "Gagal melengkapi profil lawan bicara: ${e.message}")
            emptyMap()
        }
    }

    private companion object {
        const val TAG = "ChatRepositoryImpl"

        /** Query kosong berarti "semua user selain diri sendiri" di backend. */
        const val MATCH_ALL_QUERY = ""
    }
}

/**
 * Mengubah pesan terakhir percakapan menjadi [Message] untuk daftar chat.
 *
 * Backend hanya menyimpan teks pada `lastMessage`, sehingga jenis media tidak
 * dapat diketahui dari sini dan pesan media tampil sebagai teks kosong. Karena
 * itu percakapan tanpa teks terakhir mengembalikan null agar UI menampilkan
 * "belum ada pesan" alih-alih gelembung kosong.
 */
fun ConversationDto.toLastMessage(): Message? {
    val preview = lastMessage?.takeIf { it.isNotBlank() } ?: return null
    return Message(
        messageId = id,
        sender_uid = "",
        type = MEDIA_TYPE_TEXT,
        content = preview,
        timestamp = parseIsoTimestamp(lastMessageAt ?: updatedAt)
    )
}

/**
 * Memasangkan percakapan dengan [otherUser] sebagai entri daftar chat.
 *
 * `chatId` berisi id percakapan backend (ObjectId), bukan lagi gabungan uid
 * seperti pada jalur Firebase.
 */
fun ConversationDto.toRecentChat(otherUser: User): RecentChat {
    return RecentChat(
        user = otherUser,
        chatId = id,
        lastMessage = toLastMessage()
    )
}

/**
 * Menyisipkan pesan yang baru tiba ke daftar percakapan yang sudah dimuat.
 *
 * Percakapan yang belum ada di daftar (mis. baru dibuat dari perangkat lain)
 * tidak dapat ditambahkan karena profil lawan bicaranya belum diketahui, jadi
 * diserahkan ke pemuatan ulang berikutnya. Bila percakapan ditemukan, entri
 * dipindahkan ke urutan paling atas supaya sesuai dengan urutan backend.
 */
fun List<RecentChat>.applyIncomingMessage(
    conversationId: String,
    lastMessage: Message?
): List<RecentChat> {
    val index = indexOfFirst { it.chatId == conversationId }
    if (index == -1) return this

    val updated = this[index].copy(lastMessage = lastMessage)
    return buildList(size) {
        add(updated)
        addAll(this@applyIncomingMessage.filterIndexed { position, _ -> position != index })
    }
}

/**
 * Memetakan pesan socket ke model domain. Dipisahkan dari
 * [ChatMessageDto.toMessage] agar percakapan yang belum diketahui tetap bisa
 * dipakai: `conversationId` dibutuhkan untuk memutuskan apakah pesan termasuk
 * percakapan yang sedang dibuka.
 */
fun ChatMessageDto.conversationIdOrNull(): String? = conversationId?.takeIf { it.isNotBlank() }

/** `true` bila pesan ditulis oleh [userId]. */
fun ChatMessageDto.isFrom(userId: String?): Boolean {
    return userId != null && senderId == userId
}
