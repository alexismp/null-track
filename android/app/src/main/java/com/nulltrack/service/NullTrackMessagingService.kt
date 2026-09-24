package com.nulltrack.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nulltrack.MainActivity
import com.nulltrack.data.AlertRepository
import com.nulltrack.data.TrainAlert

class NullTrackMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nouveau token FCM reçu : $token")
        // Réabonnement automatique au topic de surveillance
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_NAME)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Abonné avec succès au topic $TOPIC_NAME")
                } else {
                    Log.e(TAG, "Erreur abonnement topic $TOPIC_NAME", task.exception)
                }
            }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message FCM reçu : ${remoteMessage.data}")

        val data = remoteMessage.data
        val title = remoteMessage.notification?.title ?: data["title"] ?: "⚠️ Train Annulé"
        val body = remoteMessage.notification?.body ?: data["body"] ?: "Un train Ligne N vers Montparnasse est supprimé."

        val trainId = data["train_id"] ?: System.currentTimeMillis().toString()
        val missionCode = data["mission_code"] ?: "Ligne N"
        val departureTime = data["departure_time"] ?: "--:--"
        val stopName = data["stop_name"] ?: "Meudon"
        val destination = data["destination"] ?: "Paris-Montparnasse"

        // 1. Sauvegarde dans l'historique local de l'application
        val alert = TrainAlert(
            id = trainId,
            missionCode = missionCode,
            departureTime = departureTime,
            stopName = stopName,
            destination = destination,
            status = "ANNULÉ"
        )
        AlertRepository.getInstance(applicationContext).addAlert(alert)

        // 2. Affichage de la notification système haute priorité
        showSystemNotification(title, body)
    }

    private fun showSystemNotification(title: String, body: String) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Création du canal de notification pour Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alertes Trains Annulés",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications immédiates lors de suppressions de trains Ligne N"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)

        val notificationId = (System.currentTimeMillis() % 10000).toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "NullTrackFCM"
        const val CHANNEL_ID = "TRAIN_ALERTS"
        const val TOPIC_NAME = "trains_meudon_montparnasse"
    }
}
