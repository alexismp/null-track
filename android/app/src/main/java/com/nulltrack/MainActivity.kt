package com.nulltrack

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.nulltrack.data.AlertRepository
import com.nulltrack.data.DeparturesRepository
import com.nulltrack.data.ScheduleRepository
import com.nulltrack.data.TrainAlert
import com.nulltrack.location.LocationHelper
import com.nulltrack.service.NullTrackMessagingService
import com.nulltrack.ui.HomeScreen
import com.nulltrack.ui.SettingsSheet
import com.nulltrack.ui.theme.NullTrackTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: AlertRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var departuresRepository: DeparturesRepository
    private var isSubscribedToTopic by mutableStateOf(false)

    // Demande des permissions Notifications & Localisation
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            Log.d(TAG, "Permission localisation accordée pour détection du sens de trajet")
            com.nulltrack.widget.NullTrackWidgetProvider.updateAllWidgets(applicationContext)
        }
        if (!notifGranted) {
            Toast.makeText(
                this,
                "Veuillez autoriser les notifications pour recevoir les alertes",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AlertRepository.getInstance(applicationContext)
        scheduleRepository = ScheduleRepository.getInstance(applicationContext)
        departuresRepository = DeparturesRepository.getInstance(applicationContext)

        handleOpenDeparturesIntent(intent)
        checkAppPermissions()
        subscribeToFCMTopic()

        setContent {
            NullTrackTheme {
                val alerts by repository.alerts.collectAsState()
                val schedule by scheduleRepository.schedule.collectAsState()
                val departures by departuresRepository.departures.collectAsState()
                val lastUpdatedDepartures by departuresRepository.lastUpdated.collectAsState()
                val isDeparturesLoading by departuresRepository.isLoading.collectAsState()

                var showSettingsSheet by remember { mutableStateOf(false) }

                HomeScreen(
                    schedule = schedule,
                    departures = departures,
                    lastUpdatedDepartures = lastUpdatedDepartures,
                    isDeparturesLoading = isDeparturesLoading,
                    onSettingsClick = { showSettingsSheet = true },
                    onRefreshDepartures = { departuresRepository.refresh() },
                    onTriggerQuickMonitoring = { duration, direction ->
                        scheduleRepository.resumeNow()
                        scheduleRepository.triggerQuickMonitoring(duration, direction)
                        departuresRepository.refresh()
                        com.nulltrack.widget.NullTrackWidgetProvider.updateAllWidgets(applicationContext)
                        val dirLabel = if (direction == "TO_PARIS") "Meudon ➔ Paris" else "Paris ➔ Meudon"
                        Toast.makeText(
                            this@MainActivity,
                            "Surveillance activée pour ${duration} min ($dirLabel)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onCancelQuickMonitoring = {
                        scheduleRepository.cancelQuickMonitoring()
                        if (scheduleRepository.schedule.value.isWindowActiveNow()) {
                            scheduleRepository.pauseForToday()
                        }
                        com.nulltrack.widget.NullTrackWidgetProvider.updateAllWidgets(applicationContext)
                        Toast.makeText(
                            this@MainActivity,
                            "Surveillance désactivée",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )

                if (showSettingsSheet) {
                    SettingsSheet(
                        schedule = schedule,
                        alerts = alerts,
                        isSubscribed = isSubscribedToTopic,
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
                        },
                        onTestAlertClick = { sendLocalTestAlert() },
                        onClearHistoryClick = { repository.clearAlerts() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenDeparturesIntent(intent)
    }

    private fun handleOpenDeparturesIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DEPARTURES, false) == true) {
            departuresRepository.refresh()
            com.nulltrack.widget.NullTrackWidgetProvider.updateAllWidgets(applicationContext)
        }
    }

    override fun onResume() {
        super.onResume()
        departuresRepository.refresh()
        com.nulltrack.widget.NullTrackWidgetProvider.updateAllWidgets(applicationContext)
    }

    private fun checkAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
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

        val testIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_DEPARTURES, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            999,
            testIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val builder = NotificationCompat.Builder(this, NullTrackMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Test Alerte : ROPO (08:12)")
            .setContentText("Le train de 08:12 au départ de Meudon vers Paris-Montparnasse est supprimé.")
            .setSound(defaultSound)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(999, builder.build())
        Toast.makeText(this, "Alerte de test déclenchée ! Cliquez sur la notification pour voir les départs.", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_OPEN_DEPARTURES = "open_departures"
    }
}
