package com.pkmk.bravy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pkmk.bravy.data.remote.socket.ChatNotificationSync
import com.pkmk.bravy.data.repository.AuthRepository
import com.pkmk.bravy.ui.view.chat.DetailPrivateChatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Penerima pesan Firebase Cloud Messaging.
 *
 * Dua jenis pesan ditangani:
 * - Notifikasi biasa (`notification` payload), seperti fitur komunitas/misi yang
 *   belum dimigrasikan; perilakunya dipertahankan apa adanya.
 * - Pesan chat privat dari backend (`data.type = "private_chat"`). Pesan seperti
 *   ini tidak selalu membawa `notification` payload, sehingga judul dan isinya
 *   disusun di sini: aplikasi yang sedang terlihat cukup memuat ulang datanya,
 *   sedangkan aplikasi yang sedang tidak terlihat menampilkan notifikasi yang
 *   membuka [DetailPrivateChatActivity] saat disentuh.
 */
@AndroidEntryPoint
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var chatNotificationSync: ChatNotificationSync

    /** Dipakai untuk mengirim token ke backend, yang tidak boleh menunggu UI. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "From: ${remoteMessage.from}")

        val chatConversationId = remoteMessage.chatConversationId()
        if (chatConversationId != null) {
            // Pesan chat tidak pernah ditampilkan ganda: saat aplikasi terlihat
            // soket sudah memperbarui layar, saat tidak terlihat notifikasi ini
            // yang menjadi satu-satunya pemberitahuan.
            handlePrivateChatMessage(remoteMessage, chatConversationId)
            return
        }

        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        sendTokenToServer(token)
    }

    /**
     * Mengirim token ke backend lewat `PATCH /users/fcm-token`; jalur Firebase
     * lama tetap dipertahankan agar fitur yang belum dimigrasikan tidak rusak.
     */
    private fun sendTokenToServer(token: String?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null && token != null) {
            FirebaseDatabase.getInstance().getReference("user_fcm_tokens")
                .child(userId)
                .setValue(token)
        }

        if (token.isNullOrBlank()) return
        if (!authRepository.hasActiveSession()) {
            // Tanpa sesi backend, permintaan pasti ditolak 401 dan token akan
            // dikirim ulang oleh LoginActivity setelah pengguna masuk.
            Log.d(TAG, "Token FCM ditunda: sesi backend belum tersedia")
            return
        }
        scope.launch {
            authRepository.updateFcmToken(token)
                .onFailure { Log.w(TAG, "Gagal mengirim token FCM: ${it.message}") }
        }
    }

    /**
     * `conversationId` dari data payload, atau null bila pesan ini bukan pesan
     * chat privat. Pesan tanpa id percakapan tidak dapat dibuka dari notifikasi,
     * sehingga diperlakukan sebagai notifikasi biasa.
     */
    private fun RemoteMessage.chatConversationId(): String? {
        val data = data
        if (data[TYPE_KEY] != TYPE_PRIVATE_CHAT) return null
        return data[CONVERSATION_ID_KEY]?.takeIf { it.isNotBlank() }
    }

    private fun handlePrivateChatMessage(remoteMessage: RemoteMessage, conversationId: String) {
        if (isAppVisible()) {
            // Layar chat memuat ulang percakapan yang bersangkutan; pesan teks
            // hanya lewat sini karena backend tidak mengirim `notification`
            // payload untuk chat privat.
            chatNotificationSync.requestRefresh(conversationId)
            return
        }

        val notification = remoteMessage.notification
        sendNotification(
            title = notification?.title ?: DEFAULT_CHAT_TITLE,
            messageBody = notification?.body ?: bodyOf(remoteMessage.data[BODY_KEY]),
            conversationId = conversationId
        )
    }

    private fun isAppVisible(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun bodyOf(text: String?): String {
        return text?.takeIf { it.isNotBlank() } ?: DEFAULT_BODY_TEXT
    }

    /**
     * Menampilkan notifikasi. [conversationId] yang tidak null membuat notifikasi
     * membuka [DetailPrivateChatActivity] alih-alih sekadar membuka aplikasi.
     */
    private fun sendNotification(
        title: String?,
        messageBody: String?,
        conversationId: String? = null
    ) {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = "Default Notifications"

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent(conversationId))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // --- INI BAGIAN PENTING UNTUK ANDROID 8.0+ ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId(conversationId), notificationBuilder.build())
    }

    /**
     * Notifikasi chat memakai satu id tetap per percakapan supaya pesan beruntun
     * saling menggantikan; notifikasi lain tetap memakai waktu agar tidak
     * bertumpuk menjadi satu.
     */
    private fun notificationId(conversationId: String?): Int {
        return conversationId?.hashCode() ?: System.currentTimeMillis().toInt()
    }

    /**
     * Notifikasi chat membuka layar percakapan langsung, sedangkan notifikasi
     * lain hanya membuka aplikasi.
     *
     * `FLAG_ACTIVITY_NEW_TASK` wajib karena intent ini dijalankan dari luar
     * Activity; `FLAG_ACTIVITY_CLEAR_TOP` dipakai agar menekan notifikasi
     * berkali-kali tidak menumpuk layar percakapan yang sama.
     */
    private fun openIntent(conversationId: String?): PendingIntent {
        val intent = if (conversationId == null) {
            packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent(this, DetailPrivateChatActivity::class.java).apply {
                putExtra(DetailPrivateChatActivity.EXTRA_CHAT_ID, conversationId)
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // Kode permintaan dibedakan per percakapan agar notifikasi percakapan
        // berbeda tidak saling menimpa intent satu sama lain.
        val requestCode = conversationId?.hashCode() ?: DEFAULT_REQUEST_CODE
        return PendingIntent.getActivity(this, requestCode, intent, flags)
    }

    companion object {
        /** Nilai `type` pada data payload untuk pesan chat privat. */
        const val TYPE_PRIVATE_CHAT = "private_chat"

        private const val TAG = "FcmMessagingService"
        private const val NOTIFICATION_CHANNEL_ID = "DEFAULT_NOTIFICATION_CHANNEL_ID"
        private const val TYPE_KEY = "type"
        private const val CONVERSATION_ID_KEY = "conversationId"
        private const val BODY_KEY = "body"
        private const val DEFAULT_CHAT_TITLE = "Pesan baru"

        /** Dipakai bila backend tidak mengirim teks pesan, mis. pesan media. */
        private const val DEFAULT_BODY_TEXT = "[Media]"

        private const val DEFAULT_REQUEST_CODE = 0
    }
}
