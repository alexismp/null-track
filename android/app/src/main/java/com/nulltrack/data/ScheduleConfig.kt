package com.nulltrack.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ScheduleConfig(
    val enabled: Boolean = true,
    val startHour: Int = 7,
    val startMinute: Int = 0,
    val endHour: Int = 9,
    val endMinute: Int = 30,
    val activeDays: List<Int> = listOf(0, 1, 2, 3, 4), // 0=Lundi, 1=Mardi, ..., 6=Dimanche
    val frequencyMinutes: Int = 3,
    val pausedUntil: String? = null // Format ISO UTC "yyyy-MM-dd'T'HH:mm:ss'Z'"
) {
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

    fun formatTimeRange(): String {
        return String.format(Locale.FRANCE, "%02dh%02d - %02dh%02d", startHour, startMinute, endHour, endMinute)
    }

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
            "start_hour" to startHour,
            "start_minute" to startMinute,
            "end_hour" to endHour,
            "end_minute" to endMinute,
            "active_days" to activeDays,
            "frequency_minutes" to frequencyMinutes,
            "paused_until" to pausedUntil
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): ScheduleConfig {
            val enabled = (map["enabled"] as? Boolean) ?: true
            val startHour = (map["start_hour"] as? Number)?.toInt() ?: 7
            val startMinute = (map["start_minute"] as? Number)?.toInt() ?: 0
            val endHour = (map["end_hour"] as? Number)?.toInt() ?: 9
            val endMinute = (map["end_minute"] as? Number)?.toInt() ?: 30
            val frequencyMinutes = (map["frequency_minutes"] as? Number)?.toInt() ?: 3

            val rawDays = map["active_days"] as? List<*>
            val activeDays = rawDays?.mapNotNull { (it as? Number)?.toInt() } ?: listOf(0, 1, 2, 3, 4)

            val pausedUntil = map["paused_until"] as? String

            return ScheduleConfig(
                enabled = enabled,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                activeDays = activeDays,
                frequencyMinutes = frequencyMinutes,
                pausedUntil = pausedUntil
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
