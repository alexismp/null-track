/*
 * Copyright 2026 Alexis Moussine-Pouchkine
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import com.nulltrack.data.DeparturesRepository
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

                if (current.isMonitoringActiveNow()) {
                    // Arrêt de la surveillance
                    repository.cancelQuickMonitoring()
                    if (current.isWindowActiveNow()) {
                        repository.pauseForToday()
                    }
                } else {
                    // Reprise et activation de la surveillance
                    if (current.isPaused()) {
                        repository.resumeNow()
                    }
                    val detection = LocationHelper.detectCommuteDirection(context)
                    val duration = current.quickMonitoringDurationMinutes
                    repository.triggerQuickMonitoring(
                        durationMinutes = duration,
                        direction = detection.direction.code,
                        source = "widget"
                    )
                }

                DeparturesRepository.getInstance(context).refresh()
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
                val isActive = schedule.isMonitoringActiveNow()

                val departuresRepo = DeparturesRepository.getInstance(context)
                val hasDisruption = departuresRepo.hasDisruptions(schedule.minDelayMinutes)
                val disruptionSummary = departuresRepo.getDisruptionSummary(schedule.minDelayMinutes)

                // Intent universel pour ouvrir l'application sur le tableau des départs
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

                // 1. État visuel et message trafic (minimaliste)
                if (hasDisruption) {
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background_alert)
                    views.setTextViewText(
                        R.id.widget_detail_text,
                        disruptionSummary ?: "🚨 Incident signalé"
                    )
                    views.setTextColor(R.id.widget_detail_text, Color.parseColor("#FFFFFF"))
                    views.setInt(R.id.widget_open_app_button, "setBackgroundResource", R.drawable.widget_button_alert)
                } else {
                    views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_background)
                    views.setTextViewText(R.id.widget_detail_text, "Trafic normal")
                    views.setTextColor(R.id.widget_detail_text, Color.parseColor("#94A3B8"))
                    views.setInt(R.id.widget_open_app_button, "setBackgroundResource", R.drawable.widget_button_inactive)
                }

                // 2. Bouton d'action et d'état de surveillance (porte l'état lui-même)
                if (isActive) {
                    val remaining = schedule.getActiveRemainingText()
                    val label = if (remaining != null) "⏹️ Surveillance active ($remaining)" else "⏹️ Surveillance active"
                    views.setTextViewText(R.id.widget_action_button, label)
                    views.setInt(
                        R.id.widget_action_button,
                        "setBackgroundResource",
                        R.drawable.widget_button_active
                    )
                } else {
                    views.setTextViewText(R.id.widget_action_button, "▶️ Démarrer la surveillance")
                    views.setInt(
                        R.id.widget_action_button,
                        "setBackgroundResource",
                        R.drawable.widget_button_inactive
                    )
                }

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

                // 3. Clics pour ouvrir l'application (Détails, logo, texte, fond)
                views.setOnClickPendingIntent(R.id.widget_open_app_button, appPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_header, appPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_app_logo, appPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_detail_text, appPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_root, appPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(TAG, "Widget $appWidgetId mis à jour (actif=$isActive, perturbation=$hasDisruption)")
            } catch (e: Throwable) {
                Log.e(TAG, "Exception lors de la mise à jour du widget $appWidgetId: ${e.message}", e)
            }
        }
    }
}
