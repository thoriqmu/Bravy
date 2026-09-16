package com.pkmk.bravy.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.pkmk.bravy.data.model.Message
import com.pkmk.bravy.data.model.User

/**
 * Percakapan privat dari backend (`GET/POST /api/v1/chat`).
 *
 * Berbeda dari model Firebase lama yang memakai `chatId` berisi gabungan UID,
 * backend memakai `_id` ObjectId Mongo. [participants] berisi id kedua pihak,
 * sehingga lawan bicara ditentukan dengan menyaring [CURRENT_USER_ID].
 */
data class ConversationDto(
    @SerializedName("_id") val id: String = "",
    @SerializedName("participants") val participants: List<String>? = null,
    @SerializedName("lastMessage") val lastMessage: String? = null,
    @SerializedName("lastMessageAt") val lastMessageAt: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
) {
    /** Id lawan bicara, atau null bila percakapan tidak berisi dua peserta. */
    fun otherParticipantId(currentUserId: String?): String? {
        return participants.orEmpty().firstOrNull { it != currentUserId }
    }

    companion object {
        /**
         * `conversation:join` tidak menerima payload dari server, jadi lawan
         * bicara ditandai dengan nilai sentinel ini saat percakapan baru dibuat
         * sebelum id lawan diketahui. Nilai ini tidak akan pernah sama dengan
         * ObjectId mana pun.
         */
        const val CURRENT_USER_ID = "current-user"
    }
}

/**
 * [ConversationDto] yang sudah dipasangkan dengan profil lawan bicara supaya
 * langsung bisa ditampilkan di daftar chat.
 */
data class ConversationWithUser(
    val conversation: ConversationDto,
    val otherUser: User
)

/** Body untuk `POST /api/v1/chat`. */
data class CreateConversationRequest(
    @SerializedName("participantId") val participantId: String
)

/**
 * Pesan dari backend, baik lewat `GET /api/v1/chat/:id/messages` maupun event
 * `chat:receive`.
 *
 * [clientMessageId] dikirim kembali apa adanya oleh server, sehingga pesan
 * optimistis di sisi klien dapat dicocokkan dan digantikan tanpa duplikasi.
 */
data class ChatMessageDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("mediaType") val mediaType: String? = null,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("read") val read: Boolean = false,
    @SerializedName("clientMessageId") val clientMessageId: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

/** Body untuk `POST /api/v1/chat/:id/messages`. */
data class SendMessageRequest(
    @SerializedName("text") val text: String? = null,
    @SerializedName("mediaType") val mediaType: String = MEDIA_TYPE_TEXT,
    @SerializedName("mediaUrl") val mediaUrl: String? = null,
    @SerializedName("clientMessageId") val clientMessageId: String? = null
)

/** Payload `chat:receive`, `typing:start`, dan `typing:stop` dari server. */
data class ChatEventDto(
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName("userId") val userId: String? = null
)

/** Payload `conversation:error`. */
data class ConversationErrorDto(
    @SerializedName("conversationId") val conversationId: String? = null,
    @SerializedName("message") val message: String? = null
)

/**
 * Memetakan pesan backend ke model domain [Message] yang dipakai adapter.
 *
 * Field yang tidak dipunya backend (mis. `duration` untuk voice note) dibiarkan
 * pada nilai bawaannya. Jenis media dipetakan apa adanya sehingga gambar dan
 * voice note tetap dikenali UI.
 */
fun ChatMessageDto.toMessage(): Message {
    return Message(
        messageId = id.orEmpty(),
        sender_uid = senderId.orEmpty(),
        type = mediaType?.takeIf { it.isNotBlank() } ?: MEDIA_TYPE_TEXT,
        content = mediaUrl?.takeIf { it.isNotBlank() } ?: text.orEmpty(),
        timestamp = parseIsoTimestamp(createdAt)
    )
}

const val MEDIA_TYPE_TEXT = "text"
