package com.pkmk.bravy.data.remote.socket

import com.pkmk.bravy.data.remote.dto.ChatMessageDto

/**
 * Permintaan `conversation:join` dan `conversation:leave`.
 *
 * Backend menerima baik string polos maupun objek `{ conversationId }`
 * (lihat `readConversationId`), dan tipe ini memilih bentuk objek.
 */
data class ConversationRequest(val conversationId: String)

/** Permintaan `typing:start` dan `typing:stop`. */
data class TypingRequest(val conversationId: String)

/** Event `chat:read` dari server; menandai lawan bicara membaca percakapan. */
data class ChatReadEvent(
    val conversationId: String,
    val userId: String
)

/** Event `typing:start` / `typing:stop` dari lawan bicara. */
data class TypingEvent(
    val conversationId: String,
    val userId: String
)

/**
 * Hasil dekode `socket.handshake.auth` / koneksi.
 *
 * Nilai token sengaja tidak ikut dicatat di log mana pun.
 */
sealed interface SocketConnectionState {
    data object Disconnected : SocketConnectionState
    data object Connecting : SocketConnectionState
    data object Connected : SocketConnectionState
    data class Error(val message: String?) : SocketConnectionState
}

/** Event yang dipancarkan server dan dikonsumsi ViewModel lewat [SocketManager.events]. */
sealed interface SocketEvent {
    data class MessageReceived(val message: ChatMessageDto) : SocketEvent
    data class ConversationJoined(val conversationId: String) : SocketEvent
    data class ConversationError(val conversationId: String?, val message: String?) : SocketEvent
    data class MessagesRead(val event: ChatReadEvent) : SocketEvent
    data class TypingStarted(val event: TypingEvent) : SocketEvent
    data class TypingStopped(val event: TypingEvent) : SocketEvent
    data class ConnectionStateChanged(val state: SocketConnectionState) : SocketEvent
}
