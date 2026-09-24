package com.nulltrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.nulltrack.data.AlertRepository
import com.nulltrack.data.TrainAlert
import com.nulltrack.service.NullTrackMessagingService
import com.nulltrack.ui.HomeScreen
import com.nulltrack.ui.theme.NullTrackTheme

import androidx.compose.runtime.remember
import com.nulltrack.data.ScheduleRepository
import com.nulltrack.ui.SettingsSheet

class MainActivity : ComponentActivity() {

    private lateinit var repository: AlertRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private var isSubscribedToTopic by mutableStateOf(false)

    // Demande de permission POST_NOTIFICATIONS pour Android 13+ (API 33+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "Permission notifications accordée")
        } else {
            Toast.makeText(
                this,
                "Veuillez autoriser les notifications pour être alerté des annulations",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AlertRepository.getInstance(applicationContext)
        scheduleRepository = ScheduleRepository.getInstance(applicationContext)

        checkNotificationPermission()
        subscribeToFCMTopic()

        setContent {
            NullTrackTheme {
                val alerts by repository.alerts.collectAsState()
                val schedule by scheduleRepository.schedule.collectAsState()
                var showSettingsSheet by remember { mutableStateOf(false) }

                HomeScreen(
                    alerts = alerts,
                    isSubscribed = isSubscribedToTopic,
                    schedule = schedule,
                    onTestAlertClick = { sendLocalTestAlert() },
                    onClearHistoryClick = { repository.clearAlerts() },
                    onSettingsClick = { showSettingsSheet = true }
                )

                if (showSettingsSheet) {
                    SettingsSheet(
                        schedule = schedule,
                        onDismiss = { showSettingsSheet = false },
                        onSaveSchedule = { newSchedule ->
                            scheduleRepository.saveConfig(newSchedule)
                        },
                        onPauseHours = { hours ->
                            scheduleRepository.pauseForHours(hours)
                        },
                        onPauseToday = {
                            scheduleRepository.pauseForToday()
                        },
                        onResumeNow = {
                            scheduleRepository.resumeNow()
                        }
                    )
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun subscribeToFCMTopic() {
        FirebaseMessaging.getInstance().subscribeToTopic(NullTrackMessagingService.TOPIC_NAME)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    isSubscribedToTopic = true
                    Log.d(TAG, "Abonné au topic ${NullTrackMessagingService.TOPIC_NAME}")
                } else {
                    isSubscribedToTopic = false
                    Log.e(TAG, "Échec abonnement topic", task.exception)
                }
            }
    }

    private fun sendLocalTestAlert() {
        val testAlert = TrainAlert(
            id = "test_${System.currentTimeMillis()}",
            missionCode = "ROPO",
            departureTime = "08:12",
            stopName = "Meudon",
            destination = "Paris-Montparnasse",
            status = "ANNULÉ"
        )
        repository.addAlert(testAlert)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NullTrackMessagingService.CHANNEL_ID,
                "Alertes Trains Annulés",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(this, NullTrackMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Test Alerte : ROPO (08:12)")
            .setContentText("Le train de 08:12 au départ de Meudon vers Paris-Montparnasse est supprimé.")
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)

        notificationManager.notify(999, builder.build())
        Toast.makeText(this, "Alerte de test déclenchée !", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
