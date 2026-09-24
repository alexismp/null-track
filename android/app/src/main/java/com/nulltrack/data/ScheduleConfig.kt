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

package com.nulltrack.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScheduleConfig(
    val enabled: Boolean = true,
    // Plage Matin : Meudon ➔ Paris-Montparnasse
    val morningEnabled: Boolean = true,
    val morningStartHour: Int = 7,
    val morningStartMinute: Int = 0,
    val morningEndHour: Int = 9,
    val morningEndMinute: Int = 30,
    // Plage Soir : Paris-Montparnasse ➔ Meudon
    val eveningEnabled: Boolean = true,
    val eveningStartHour: Int = 17,
    val eveningStartMinute: Int = 0,
    val eveningEndHour: Int = 19,
    val eveningEndMinute: Int = 30,
    // Jours actifs & Fréquence
    val activeDays: List<Int> = listOf(0, 1, 2, 3, 4), // 0=Lundi, 1=Mardi, ..., 6=Dimanche
    val frequencyMinutes: Int = 3,
    // Mise en pause temporaire (Snooze)
    val pausedUntil: String? = null, // Format ISO UTC "yyyy-MM-dd'T'HH:mm:ss'Z'"
    // Déclencheur ponctuel retour travail (rétrocompatibilité)
    val returnCommuteUntil: String? = null, // Format ISO UTC
    // Surveillance ponctuelle par Widget / Géolocalisation (durée paramétrable, défaut: 60 min)
    val quickMonitoringUntil: String? = null,
    val quickMonitoringDirection: String? = null, // "TO_PARIS", "TO_MEUDON" ou "AUTO"
    val quickMonitoringDurationMinutes: Int = 60,
    // Notification des retards en plus des annulations
    val notifyDelays: Boolean = true,
    val minDelayMinutes: Int = 5
) {
    // Rétrocompatibilité
    val startHour: Int get() = morningStartHour
    val startMinute: Int get() = morningStartMinute
    val endHour: Int get() = morningEndHour
    val endMinute: Int get() = morningEndMinute

    fun isPaused(): Boolean {
        if (pausedUntil.isNullOrBlank()) return false
        val pauseDate = parseIsoDate(pausedUntil) ?: return false
        return Date().before(pauseDate)
    }

    fun getPausedRemainingText(): String? {
        if (!isPaused() || pausedUntil == null) return null
        val pauseDate = parseIsoDate(pausedUntil) ?: return null
        val parisFormat = SimpleDateFormat("HH:mm", Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("Europe/Paris")
        }
        return parisFormat.format(pauseDate)
    }

    /**
     * Indique si une surveillance ponctuelle (Widget ou Déclencheur retour) est actuellement en cours.
     */
    fun isQuickMonitoringActive(): Boolean {
        val target = quickMonitoringUntil ?: returnCommuteUntil
        if (target.isNullOrBlank()) return false
        val retDate = parseIsoDate(target) ?: return false
        return Date().before(retDate)
    }

    fun isReturnCommuteActive(): Boolean = isQuickMonitoringActive()

    /**
     * Indique si l'heure actuelle correspond à un créneau programmé (matin ou soir).
     */
    fun isWindowActiveNow(): Boolean {
        if (!enabled || isPaused()) return false

        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
        val dayOfWeek = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        if (dayOfWeek !in activeDays) return false

        val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        if (morningEnabled) {
            val mStart = morningStartHour * 60 + morningStartMinute
            val mEnd = morningEndHour * 60 + morningEndMinute
            if (currentMinutes in mStart..mEnd) return true
        }

        if (eveningEnabled) {
            val eStart = eveningStartHour * 60 + eveningStartMinute
            val eEnd = eveningEndHour * 60 + eveningEndMinute
            if (currentMinutes in eStart..eEnd) return true
        }

        return false
    }

    /**
     * Indique si la surveillance est active à cet instant précis (ponctuelle ou programmée).
     */
    fun isMonitoringActiveNow(): Boolean {
        if (!enabled || isPaused()) return false
        if (isQuickMonitoringActive()) return true
        return isWindowActiveNow()
    }

    /**
     * Retourne l'heure d'expiration de la surveillance ponctuelle (ex: 18:45).
     */
    fun getQuickMonitoringRemainingText(): String? {
        val target = quickMonitoringUntil ?: returnCommuteUntil
        if (!isQuickMonitoringActive() || target == null) return null
        val retDate = parseIsoDate(target) ?: return null
        val parisFormat = SimpleDateFormat("HH:mm", Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("Europe/Paris")
        }
        return parisFormat.format(retDate)
    }

    fun getReturnCommuteRemainingText(): String? = getQuickMonitoringRemainingText()

    fun getActiveDirectionCode(): String? {
        if (isQuickMonitoringActive() && quickMonitoringDirection != null) {
            return quickMonitoringDirection
        }
        if (isWindowActiveNow()) {
            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
            val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val mStart = morningStartHour * 60 + morningStartMinute
            val mEnd = morningEndHour * 60 + morningEndMinute
            if (morningEnabled && currentMinutes in mStart..mEnd) {
                return "TO_PARIS"
            }
            val eStart = eveningStartHour * 60 + eveningStartMinute
            val eEnd = eveningEndHour * 60 + eveningEndMinute
            if (eveningEnabled && currentMinutes in eStart..eEnd) {
                return "TO_MEUDON"
            }
        }
        return null
    }

    fun getActiveRemainingText(): String? {
        if (isQuickMonitoringActive()) {
            return getQuickMonitoringRemainingText()
        }
        if (isWindowActiveNow()) {
            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"))
            val currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            val mStart = morningStartHour * 60 + morningStartMinute
            val mEnd = morningEndHour * 60 + morningEndMinute
            if (morningEnabled && currentMinutes in mStart..mEnd) {
                return String.format(Locale.FRANCE, "%02dh%02d", morningEndHour, morningEndMinute)
            }
            val eStart = eveningStartHour * 60 + eveningStartMinute
            val eEnd = eveningEndHour * 60 + eveningEndMinute
            if (eveningEnabled && currentMinutes in eStart..eEnd) {
                return String.format(Locale.FRANCE, "%02dh%02d", eveningEndHour, eveningEndMinute)
            }
        }
        return null
    }

    /**
     * Libellé lisible de la direction actuellement surveillée en mode ponctuel ou programmé.
     */
    fun getActiveDirectionText(): String {
        val code = getActiveDirectionCode()
        return when (code) {
            "TO_PARIS" -> "Meudon ➔ Paris"
            "TO_MEUDON" -> "Paris ➔ Meudon"
            else -> getQuickMonitoringDirectionText()
        }
    }

    /**
     * Libellé lisible de la direction actuellement surveillée en mode ponctuel.
     */
    fun getQuickMonitoringDirectionText(): String {
        return when (quickMonitoringDirection) {
            "TO_PARIS" -> "Meudon ➔ Paris"
            "TO_MEUDON" -> "Paris ➔ Meudon"
            else -> if (returnCommuteUntil != null) "Paris ➔ Meudon" else "Direction auto (Position)"
        }
    }

    fun formatMorningTimeRange(): String {
        return String.format(Locale.FRANCE, "%02dh%02d - %02dh%02d", morningStartHour, morningStartMinute, morningEndHour, morningEndMinute)
    }

    fun formatEveningTimeRange(): String {
        return String.format(Locale.FRANCE, "%02dh%02d - %02dh%02d", eveningStartHour, eveningStartMinute, eveningEndHour, eveningEndMinute)
    }

    fun formatTimeRange(): String = formatMorningTimeRange()

    fun formatActiveDays(): String {
        if (activeDays.isEmpty()) return "Aucun jour"
        if (activeDays.size == 7) return "Tous les jours"
        if (activeDays == listOf(0, 1, 2, 3, 4)) return "Lun - Ven"
        if (activeDays == listOf(5, 6)) return "Week-end"

        val dayLabels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        return activeDays.sorted().mapNotNull { index ->
            if (index in dayLabels.indices) dayLabels[index] else null
        }.joinToString(", ")
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "enabled" to enabled,
            "morning_enabled" to morningEnabled,
            "morning_start_hour" to morningStartHour,
            "morning_start_minute" to morningStartMinute,
            "morning_end_hour" to morningEndHour,
            "morning_end_minute" to morningEndMinute,
            "start_hour" to morningStartHour,
            "start_minute" to morningStartMinute,
            "end_hour" to morningEndHour,
            "end_minute" to morningEndMinute,
            "evening_enabled" to eveningEnabled,
            "evening_start_hour" to eveningStartHour,
            "evening_start_minute" to eveningStartMinute,
            "evening_end_hour" to eveningEndHour,
            "evening_end_minute" to eveningEndMinute,
            "active_days" to activeDays,
            "frequency_minutes" to frequencyMinutes,
            "paused_until" to pausedUntil,
            "return_commute_until" to returnCommuteUntil,
            "quick_monitoring_until" to quickMonitoringUntil,
            "quick_monitoring_direction" to quickMonitoringDirection,
            "quick_monitoring_duration_minutes" to quickMonitoringDurationMinutes,
            "notify_delays" to notifyDelays,
            "min_delay_minutes" to minDelayMinutes
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ScheduleConfig {
            val enabled = (map["enabled"] as? Boolean) ?: true

            val mEnabled = (map["morning_enabled"] as? Boolean) ?: true
            val mStartH = (map["morning_start_hour"] as? Number)?.toInt()
                ?: (map["start_hour"] as? Number)?.toInt() ?: 7
            val mStartM = (map["morning_start_minute"] as? Number)?.toInt()
                ?: (map["start_minute"] as? Number)?.toInt() ?: 0
            val mEndH = (map["morning_end_hour"] as? Number)?.toInt()
                ?: (map["end_hour"] as? Number)?.toInt() ?: 9
            val mEndM = (map["morning_end_minute"] as? Number)?.toInt()
                ?: (map["end_minute"] as? Number)?.toInt() ?: 30

            val eEnabled = (map["evening_enabled"] as? Boolean) ?: true
            val eStartH = (map["evening_start_hour"] as? Number)?.toInt() ?: 17
            val eStartM = (map["evening_start_minute"] as? Number)?.toInt() ?: 0
            val eEndH = (map["evening_end_hour"] as? Number)?.toInt() ?: 19
            val eEndM = (map["evening_end_minute"] as? Number)?.toInt() ?: 30

            val frequencyMinutes = (map["frequency_minutes"] as? Number)?.toInt() ?: 3

            val rawDays = map["active_days"] as? List<*>
            val activeDays = rawDays?.mapNotNull { (it as? Number)?.toInt() } ?: listOf(0, 1, 2, 3, 4)

            val pausedUntil = map["paused_until"] as? String
            val returnCommuteUntil = map["return_commute_until"] as? String
            val quickMonitoringUntil = map["quick_monitoring_until"] as? String
            val quickMonitoringDirection = map["quick_monitoring_direction"] as? String
            val quickDuration = (map["quick_monitoring_duration_minutes"] as? Number)?.toInt() ?: 60
            val notifyDelays = (map["notify_delays"] as? Boolean) ?: true
            val minDelayMinutes = (map["min_delay_minutes"] as? Number)?.toInt() ?: 5

            return ScheduleConfig(
                enabled = enabled,
                morningEnabled = mEnabled,
                morningStartHour = mStartH,
                morningStartMinute = mStartM,
                morningEndHour = mEndH,
                morningEndMinute = mEndM,
                eveningEnabled = eEnabled,
                eveningStartHour = eStartH,
                eveningStartMinute = eStartM,
                eveningEndHour = eEndH,
                eveningEndMinute = eEndM,
                activeDays = activeDays,
                frequencyMinutes = frequencyMinutes,
                pausedUntil = pausedUntil,
                returnCommuteUntil = returnCommuteUntil,
                quickMonitoringUntil = quickMonitoringUntil,
                quickMonitoringDirection = quickMonitoringDirection,
                quickMonitoringDurationMinutes = quickDuration,
                notifyDelays = notifyDelays,
                minDelayMinutes = minDelayMinutes
            )
        }

        private fun parseIsoDate(isoString: String): Date? {
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                format.parse(isoString)
            } catch (e: Exception) {
                null
            }
        }
    }
}
