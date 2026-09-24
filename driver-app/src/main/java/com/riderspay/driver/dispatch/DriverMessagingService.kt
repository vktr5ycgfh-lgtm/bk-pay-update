package com.riderspay.driver.dispatch

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.riderspay.driver.R

class DriverMessagingService : FirebaseMessagingService() {
    companion object { const val CHANNEL = "incoming_ride_v1" }

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("drivers").document(uid)
                .set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "RIDE_OFFER") return
        createChannel()
        val rideId = message.data["rideId"] ?: return
        val alert = Intent(this, RideAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data.forEach { (k,v) -> putExtra(k,v) }
        }
        val fullScreen = PendingIntent.getActivity(this, rideId.hashCode(), alert,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_auto).setColor(0xFFFFCC00.toInt())
            .setContentTitle("New auto ride · ₹${message.data["fare"] ?: "--"}")
            .setContentText("${message.data["pickup"]} → ${message.data["drop"]}")
            .setCategory(NotificationCompat.CATEGORY_CALL).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setOngoing(true)
            .setFullScreenIntent(fullScreen, true).setContentIntent(fullScreen).setTimeoutAfter(20_000).build()
        getSystemService(NotificationManager::class.java).notify(rideId.hashCode(), notification)
    }

    private fun createChannel() {
        val sound = Uri.parse("android.resource://$packageName/${R.raw.ride_alert_chime}")
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val channel = NotificationChannel(CHANNEL, "Incoming ride alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Time-sensitive 20-second ride offers"; setSound(sound, attributes); enableVibration(true)
            vibrationPattern = longArrayOf(0,350,180,350,700); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
