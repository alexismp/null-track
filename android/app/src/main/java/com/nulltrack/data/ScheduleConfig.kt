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
    // Déclencheur retour ponctuel (1h ou 2h en quittant le travail)
    val returnCommuteUntil: String? = null // Format ISO UTC
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

    fun isReturnCommuteActive(): Boolean {
        if (returnCommuteUntil.isNullOrBlank()) return false
        val retDate = parseIsoDate(returnCommuteUntil) ?: return false
        return Date().before(retDate)
    }

    fun getReturnCommuteRemainingText(): String? {
        if (!isReturnCommuteActive() || returnCommuteUntil == null) return null
        val retDate = parseIsoDate(returnCommuteUntil) ?: return null
        val parisFormat = SimpleDateFormat("HH:mm", Locale.FRANCE).apply {
            timeZone = TimeZone.getTimeZone("Europe/Paris")
        }
        return parisFormat.format(retDate)
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
            "return_commute_until" to returnCommuteUntil
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
                returnCommuteUntil = returnCommuteUntil
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
