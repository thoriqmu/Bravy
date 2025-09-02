package com.pkmk.bravy

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "From: ${remoteMessage.from}")

        remoteMessage.notification?.let {
            sendNotification(it.title, it.body)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        sendTokenToServer(token)
    }

    private fun sendTokenToServer(token: String?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null && token != null) {
            FirebaseDatabase.getInstance().getReference("user_fcm_tokens")
                .child(userId)
                .setValue(token)
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val channelId = "DEFAULT_NOTIFICATION_CHANNEL_ID"
        val channelName = "Default Notifications"

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // Ganti dengan ikon Anda
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Penting untuk notifikasi penting

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // --- INI BAGIAN PENTING UNTUK ANDROID 8.0+ ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH // Pastikan importance-nya HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}