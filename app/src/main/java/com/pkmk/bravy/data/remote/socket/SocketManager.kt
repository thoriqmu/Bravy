package com.pkmk.bravy.data.remote.socket

import android.util.Log
import com.google.gson.Gson
import com.pkmk.bravy.BuildConfig
import com.pkmk.bravy.data.remote.TokenStore
import com.pkmk.bravy.data.remote.dto.ChatEventDto
import com.pkmk.bravy.data.remote.dto.ChatMessageDto
import com.pkmk.bravy.data.remote.dto.ConversationErrorDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Satu-satunya pemilik koneksi Socket.IO ke backend Bravy.
 *
 * Kelas ini singleton karena backend memakai satu namespace default: membuka
 * lebih dari satu koneksi berarti setiap event `chat:receive` diterima berkali-kali.
 * Seluruh ViewModel chat berbagi instance ini dan menyaring event berdasarkan
 * `conversationId`.
 *
 * Token tidak diambil dari header Authorization seperti REST, melainkan dari
 * `socket.handshake.auth.token` yang dikirim pada paket CONNECT (lihat
 * `Options.auth`). Token diambil ulang setiap kali [ensureConnected] dipanggil
 * agar koneksi hasil reconnect selalu memakai access token terbaru.
 */
@Singleton
class SocketManager @Inject constructor(
    private val tokenStore: TokenStore,
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectMutex = Mutex()

    private var socket: Socket? = null
    private var listenerJobs: MutableList<Job> = mutableListOf()

    /**
     * `extraBufferCapacity` dipakai agar `tryEmit` dari thread Socket.IO tidak
     * memblokir; event yang datang saat tidak ada pelanggan akan dibuang, yang
     * aman karena pesan lengkap selalu bisa diambil ulang lewat REST.
     */
    private val _events = MutableSharedFlow<SocketEvent>(
        replay = 0,
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    /** `true` bila soket ada dan sedang terhubung. */
    val isConnected: Boolean
        get() = socket?.connected() == true

    /**
     * Memastikan koneksi terbuka dan terautentikasi. Aman dipanggil berulang kali
     * dari `onResume` maupun `onStart` layar chat: koneksi yang sudah terbuka
     * tidak dibuka ulang.
     */
    fun ensureConnected() {
        scope.launch {
            connectMutex.withLock {
                val existing = socket
                if (existing != null && (existing.connected() || existing.isActive)) return@withLock
                connectLocked()
            }
        }
    }

    /** Menutup koneksi beserta seluruh listener-nya, mis. saat pengguna keluar. */
    fun disconnect() {
        scope.launch {
            connectMutex.withLock {
                teardownLocked()
            }
        }
    }

    /**
     * Bergabung ke room percakapan. Backend hanya mengizinkan peserta percakapan
     * yang sah; penolakan dilaporkan lewat event `conversation:error` dan
     * diteruskan sebagai [SocketEvent.ConversationError].
     */
    fun joinConversation(conversationId: String) {
        emitToServer(EVENT_JOIN, ConversationRequest(conversationId))
    }

    /** Keluar dari room percakapan, dipanggil saat layar chat ditinggalkan. */
    fun leaveConversation(conversationId: String) {
        emitToServer(EVENT_LEAVE, ConversationRequest(conversationId))
    }

    /**
     * Mengirim status sedang menulis. Backend hanya meneruskan event ini bila
     * pengirim sudah bergabung ke room percakapan.
     */
    fun emitTyping(conversationId: String, isTyping: Boolean) {
        val event = if (isTyping) EVENT_TYPING_START else EVENT_TYPING_STOP
        emitToServer(event, TypingRequest(conversationId))
    }

    /** Mengirim payload sebagai JSON; `null` bila soket belum siap. */
    private fun emitToServer(event: String, payload: Any) {
        val active = socket
        if (active == null || !active.connected()) {
            Log.w(TAG, "Melewati $event: soket belum terhubung")
            return
        }
        active.emit(event, gson.toJson(payload))
    }

    private fun connectLocked() {
        val token = tokenStore.getAccessToken()
        if (token.isNullOrBlank()) {
            // Tanpa token, handshake pasti ditolak; UI akan menampilkan daftar
            // kosong sampai sesi tersedia.
            Log.w(TAG, "Tidak dapat terhubung: access token belum tersedia")
            emitState(SocketConnectionState.Error(TOKEN_MISSING_MESSAGE))
            return
        }

        val options = buildOptions(token)
        if (options == null) {
            emitState(SocketConnectionState.Error(INVALID_URL_MESSAGE))
            return
        }

        emitState(SocketConnectionState.Connecting)
        val created = IO.socket(socketUrl(), options)
        socket = created
        registerListeners(created)
        created.connect()
    }

    /**
     * `Options.auth` diisi token agar ikut terkirim pada paket CONNECT. OkHttp
     * milik aplikasi dipakai ulang supaya koneksi websocket tidak membuka
     * connection pool kedua.
     */
    private fun buildOptions(token: String): IO.Options? {
        return try {
            IO.Options().apply {
                auth = mapOf(AUTH_TOKEN_KEY to token)
                forceNew = true
                reconnection = true
                reconnectionDelay = RECONNECT_DELAY_MS
                reconnectionDelayMax = RECONNECT_DELAY_MAX_MS
                callFactory = okHttpClient
                webSocketFactory = okHttpClient
                // Polling tetap diizinkan sebagai fallback bila websocket gagal;
                // upgrade dilakukan engine.io setelah handshake pertama.
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Menyiapkan koneksi socket ke ${socketUrl()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyiapkan opsi socket: ${e.message}")
            null
        }
    }

    /** Alamat soket = base URL REST tanpa skema ganda; namespace default. */
    private fun socketUrl(): URI? {
        return try {
            URI(BuildConfig.BRAVY_BASE_URL)
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Base URL tidak valid: ${e.message}")
            null
        }
    }

    private fun registerListeners(active: Socket) {
        listenerJobs = mutableListOf(
            observe(active, Socket.EVENT_CONNECT, SocketConnectionState.Connected),
            observe(active, Socket.EVENT_DISCONNECT, SocketConnectionState.Disconnected),
            listen(active, Socket.EVENT_CONNECT_ERROR) { args ->
                emitState(SocketConnectionState.Error(readErrorMessage(args)))
            },
            listen(active, EVENT_JOINED) { args ->
                val id = parse(args, ConversationErrorDto::class.java)?.conversationId
                if (id != null) {
                    _events.tryEmit(SocketEvent.ConversationJoined(id))
                }
            },
            listen(active, EVENT_CONVERSATION_ERROR) { args ->
                val payload = parse(args, ConversationErrorDto::class.java)
                _events.tryEmit(
                    SocketEvent.ConversationError(payload?.conversationId, payload?.message)
                )
            },
            listen(active, EVENT_CHAT_RECEIVE) { args ->
                val message = parse(args, ChatMessageDto::class.java)
                if (message?.id != null) {
                    _events.tryEmit(SocketEvent.MessageReceived(message))
                }
            },
            listen(active, EVENT_CHAT_READ) { args ->
                val payload = parse(args, ChatMessageDto::class.java)
                if (payload?.conversationId != null) {
                    _events.tryEmit(
                        SocketEvent.MessagesRead(
                            ChatReadEvent(
                                conversationId = payload.conversationId,
                                userId = payload.senderId.orEmpty()
                            )
                        )
                    )
                }
            },
            listen(active, EVENT_TYPING_START) { args ->
                val payload = parse(args, ChatEventDto::class.java)
                if (payload?.conversationId != null) {
                    _events.tryEmit(
                        SocketEvent.TypingStarted(
                            TypingEvent(payload.conversationId, payload.userId.orEmpty())
                        )
                    )
                }
            },
            listen(active, EVENT_TYPING_STOP) { args ->
                val payload = parse(args, ChatEventDto::class.java)
                if (payload?.conversationId != null) {
                    _events.tryEmit(
                        SocketEvent.TypingStopped(
                            TypingEvent(payload.conversationId, payload.userId.orEmpty())
                        )
                    )
                }
            }
        )
    }

    private fun observe(active: Socket, event: String, state: SocketConnectionState): Job {
        return listen(active, event) { emitState(state) }
    }

    /**
     * Mendaftarkan listener sekaligus mencatat Job-nya agar bisa dilepas saat
     * [teardownLocked]; tanpa ini, reconnect berulang akan menumpuk listener.
     */
    private fun listen(active: Socket, event: String, onArgs: (Array<Any>) -> Unit): Job {
        val job = scope.launch {
            active.on(event) { args -> onArgs(args) }
            // Menahan job sampai dibatalkan agar `on` tidak dilepas; pembatalan
            // ditangani pada teardown.
            awaitCancellation()
        }
        return job
    }

    private suspend fun teardownLocked() {
        listenerJobs.forEach { it.cancel() }
        listenerJobs = mutableListOf()
        socket?.let { active ->
            active.off()
            active.disconnect()
        }
        socket = null
        emitState(SocketConnectionState.Disconnected)
    }

    private fun emitState(state: SocketConnectionState) {
        _events.tryEmit(SocketEvent.ConnectionStateChanged(state))
    }

    /** Payload Socket.IO diterima sebagai [org.json.JSONObject] atau string JSON. */
    private fun <T> parse(args: Array<Any>, type: Class<T>): T? {
        val raw = args.firstOrNull()
        val json = when (raw) {
            is JSONObject -> raw.toString()
            is String -> raw
            else -> null
        } ?: return null

        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Log.w(TAG, "Gagal membaca payload socket: ${e.message}")
            null
        }
    }

    /** `connect_error` mengirim Error atau pesan teks, bukan JSON. */
    private fun readErrorMessage(args: Array<Any>): String? {
        return when (val raw = args.firstOrNull()) {
            is Throwable -> raw.message
            is String -> raw
            else -> null
        }
    }

    private companion object {
        const val TAG = "SocketManager"
        const val AUTH_TOKEN_KEY = "token"
        const val EVENT_BUFFER_CAPACITY = 64
        const val RECONNECT_DELAY_MS = 1_000L
        const val RECONNECT_DELAY_MAX_MS = 10_000L
        const val TOKEN_MISSING_MESSAGE = "Sesi tidak ditemukan, silakan masuk kembali."
        const val INVALID_URL_MESSAGE = "Alamat server tidak valid."

        const val EVENT_JOIN = "conversation:join"
        const val EVENT_LEAVE = "conversation:leave"
        const val EVENT_JOINED = "conversation:joined"
        const val EVENT_CONVERSATION_ERROR = "conversation:error"
        const val EVENT_CHAT_RECEIVE = "chat:receive"
        const val EVENT_CHAT_READ = "chat:read"
        const val EVENT_TYPING_START = "typing:start"
        const val EVENT_TYPING_STOP = "typing:stop"
    }
}
