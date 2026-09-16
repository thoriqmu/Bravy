package com.pkmk.bravy.data.repository

import com.pkmk.bravy.data.model.RecentChat
import com.pkmk.bravy.data.model.User
import com.pkmk.bravy.data.remote.dto.ChatMessageDto
import com.pkmk.bravy.data.remote.dto.ConversationDto
import com.pkmk.bravy.data.remote.dto.ConversationWithUser
import com.pkmk.bravy.data.remote.dto.MEDIA_TYPE_TEXT

/**
 * Percakapan privat lewat backend Bravy (`/api/v1/chat`).
 *
 * Berbeda dari jalur Firebase lama yang masih ada di [AuthRepository], repository
 * ini tidak menyimpan state: setiap pemanggilan berbicara langsung ke REST, dan
 * pesan yang datang lewat socket diteruskan oleh ViewModel.
 */
interface ChatRepository {

    /**
     * Percakapan milik pengguna yang sedang login, sudah dipasangkan dengan
     * profil lawan bicara dan diurutkan dari yang paling baru.
     *
     * Dipetakan ke [RecentChat] agar adapter daftar chat yang sudah ada tetap
     * dapat dipakai; `chatId` kini berisi id percakapan backend (ObjectId), bukan
     * lagi gabungan uid seperti pada jalur Firebase.
     */
    suspend fun getConversations(): Result<List<RecentChat>>

    /**
     * Satu percakapan lengkap dengan profil lawan bicaranya.
     *
     * Dipakai saat percakapan dibuka dari notifikasi: hanya `conversationId`
     * yang tersedia, sedangkan layar chat membutuhkan profil lawan bicara untuk
     * judul dan fotonya.
     */
    suspend fun getConversation(conversationId: String): Result<ConversationWithUser>

    /**
     * Profil pengguna lain berdasarkan id-nya, atau null bila tidak ditemukan.
     *
     * Backend belum punya endpoint "profil user lain", sehingga profil dicari
     * lewat `GET /users/search`.
     */
    suspend fun getUserProfile(userId: String): User?

    /**
     * Membuat percakapan dengan [participantId] atau mengembalikan yang sudah ada
     * (backend bersifat idempoten untuk pasangan peserta yang sama).
     */
    suspend fun createConversation(participantId: String): Result<ConversationDto>

    /**
     * Pesan satu halaman, diurutkan dari yang paling lama ke paling baru agar
     * bisa langsung dipakai adapter. Backend juga menandai pesan lawan sebagai
     * terbaca pada pemanggilan ini.
     */
    suspend fun getMessages(
        conversationId: String,
        page: Int = FIRST_PAGE
    ): Result<List<ChatMessageDto>>

    /**
     * Mengirim pesan. [clientMessageId] dibuat otomatis bila tidak diberikan,
     * sehingga backend dapat mengenali pengiriman ulang yang sama dan tidak
     * membuat pesan ganda.
     */
    suspend fun sendMessage(
        conversationId: String,
        text: String? = null,
        mediaType: String = MEDIA_TYPE_TEXT,
        mediaUrl: String? = null,
        clientMessageId: String? = null
    ): Result<ChatMessageDto>

    /** Menandai pesan lawan bicara pada percakapan ini sebagai sudah dibaca. */
    suspend fun markRead(conversationId: String): Result<Unit>

    /**
     * Id pengguna yang sedang login menurut backend, atau null bila tidak dapat
     * ditentukan. Dipakai untuk memisahkan pesan sendiri dari pesan lawan.
     */
    suspend fun getCurrentUserId(): String?

    /** Membuat id pesan sisi klien untuk pengiriman optimistis. */
    fun newClientMessageId(): String

    companion object {
        /** Backend memakai penomoran halaman mulai dari 1. */
        const val FIRST_PAGE = 1
    }
}
