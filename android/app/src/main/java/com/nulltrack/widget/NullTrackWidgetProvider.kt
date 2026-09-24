package com.nulltrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import com.nulltrack.MainActivity
import com.nulltrack.R
import com.nulltrack.data.ScheduleConfig
import com.nulltrack.data.ScheduleRepository
import com.nulltrack.location.LocationHelper

class NullTrackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val scheduleRepository = ScheduleRepository.getInstance(context)
        val schedule = scheduleRepository.schedule.value

        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId, schedule)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "Action widget reçue : ${intent.action}")

        when (intent.action) {
            ACTION_TOGGLE_MONITORING -> {
                val repository = ScheduleRepository.getInstance(context)
                val current = repository.schedule.value

                if (current.isQuickMonitoringActive()) {
                    // Arrêt de la surveillance
                    repository.cancelQuickMonitoring()
                } else {
                    // Activation intelligente selon la localisation
                    val detection = LocationHelper.detectCommuteDirection(context)
                    val duration = current.quickMonitoringDurationMinutes
                    repository.triggerQuickMonitoring(
                        durationMinutes = duration,
                        direction = detection.direction.code
                    )
                }

                updateAllWidgets(context)
            }
            ACTION_REFRESH, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        private const val TAG = "NullTrackWidget"
        const val ACTION_TOGGLE_MONITORING = "com.nulltrack.widget.ACTION_TOGGLE_MONITORING"
        const val ACTION_REFRESH = "com.nulltrack.widget.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NullTrackWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val schedule = ScheduleRepository.getInstance(context).schedule.value

            for (appWidgetId in appWidgetIds) {
                updateWidget(context, appWidgetManager, appWidgetId, schedule)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            schedule: ScheduleConfig
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_nulltrack)
                val isActive = schedule.isQuickMonitoringActive()

                if (isActive) {
                    views.setTextViewText(R.id.widget_status_badge, "🟢 Actif")
                    views.setTextColor(R.id.widget_status_badge, Color.parseColor("#4CAF50"))

                    views.setTextViewText(
                        R.id.widget_location_text,
                        "Sens : ${schedule.getQuickMonitoringDirectionText()}"
                    )
                    views.setTextViewText(
                        R.id.widget_detail_text,
                        "Fin : ${schedule.getQuickMonitoringRemainingText() ?: "--:--"}"
                    )

                    views.setTextViewText(R.id.widget_action_button, "⏹️ Arrêter")
                    views.setInt(
                        R.id.widget_action_button,
                        "setBackgroundResource",
                        R.drawable.widget_button_active
                    )
                } else {
                    val detection = LocationHelper.detectCommuteDirection(context)

                    views.setTextViewText(R.id.widget_status_badge, "⏸️ Inactif")
                    views.setTextColor(R.id.widget_status_badge, Color.parseColor("#94A3B8"))

                    views.setTextViewText(
                        R.id.widget_location_text,
                        "📍 ${detection.locationLabel}"
                    )
                    views.setTextViewText(
                        R.id.widget_detail_text,
                        "Sens : ${detection.direction.label}"
                    )

                    val durMinutes = schedule.quickMonitoringDurationMinutes
                    val durLabel = if (durMinutes >= 60) "${durMinutes / 60}h" else "${durMinutes}m"
                    views.setTextViewText(R.id.widget_action_button, "▶️ Activer ($durLabel)")
                    views.setInt(
                        R.id.widget_action_button,
                        "setBackgroundResource",
                        R.drawable.widget_button_inactive
                    )
                }

                // Intent pour le bouton d'action Activer / Arrêter
                val toggleIntent = Intent(context, NullTrackWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_MONITORING
                }
                val togglePendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_action_button, togglePendingIntent)

                // Intent pour ouvrir l'application sur le tableau des départs
                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_OPEN_DEPARTURES, true)
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context,
                    1,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_open_app_button, appPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_title, appPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(TAG, "Widget $appWidgetId mis à jour avec succès (actif=$isActive)")
            } catch (e: Throwable) {
                Log.e(TAG, "Exception lors de la mise à jour du widget $appWidgetId: ${e.message}", e)
            }
        }
    }
}
