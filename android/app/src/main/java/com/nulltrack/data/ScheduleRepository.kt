package com.nulltrack.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ScheduleRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _schedule = MutableStateFlow(loadLocalConfig())
    val schedule: StateFlow<ScheduleConfig> = _schedule.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null

    init {
        initFirestoreSync()
    }

    private fun initFirestoreSync() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(SETTINGS_COLLECTION).document(SCHEDULE_DOC)

            firestoreListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Erreur écoute Firestore pour schedule: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data
                    if (data != null) {
                        val remoteConfig = ScheduleConfig.fromMap(data)
                        _schedule.value = remoteConfig
                        saveLocalConfig(remoteConfig)
                        Log.d(TAG, "Configuration synchronisée depuis Firestore: $remoteConfig")
                    }
                } else if (snapshot != null && !snapshot.exists()) {
                    // Document n'existe pas encore, on le crée avec les valeurs actuelles
                    val current = _schedule.value
                    docRef.set(current.toMap(), SetOptions.merge())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore non initialisé (mode local actif): ${e.message}")
        }
    }

    fun saveConfig(newConfig: ScheduleConfig) {
        _schedule.value = newConfig
        saveLocalConfig(newConfig)

        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(SETTINGS_COLLECTION).document(SCHEDULE_DOC)
                .set(newConfig.toMap(), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d(TAG, "Configuration mise à jour dans Firestore avec succès.")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Échec mise à jour Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Impossible d'écrire dans Firestore: ${e.message}")
        }
    }

    fun toggleEnabled() {
        val current = _schedule.value
        saveConfig(current.copy(enabled = !current.enabled))
    }

    fun pauseForHours(hours: Int) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.HOUR_OF_DAY, hours)
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pauseIso = isoFormat.format(cal.time)
        val current = _schedule.value
        saveConfig(current.copy(pausedUntil = pauseIso))
    }

    fun pauseForToday() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris")).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val pauseIso = isoFormat.format(cal.time)
        val current = _schedule.value
        saveConfig(current.copy(pausedUntil = pauseIso))
    }

    fun resumeNow() {
        val current = _schedule.value
        saveConfig(current.copy(pausedUntil = null, enabled = true))
    }

    /**
     * Déclenche une fenêtre de surveillance pour le retour du travail (Paris ➔ Meudon).
     * @param hours Nombre d'heures de surveillance (ex: 1 ou 2 heures).
     */
    fun triggerReturnCommute(hours: Int = 1) {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.HOUR_OF_DAY, hours)
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val returnUntilIso = isoFormat.format(cal.time)
        val current = _schedule.value
        saveConfig(current.copy(returnCommuteUntil = returnUntilIso, enabled = true))
        Log.i(TAG, "Surveillance retour travail activée jusqu'à $returnUntilIso (${hours}h)")
    }

    /**
     * Annule la surveillance retour ponctuelle.
     */
    fun cancelReturnCommute() {
        val current = _schedule.value
        saveConfig(current.copy(returnCommuteUntil = null))
        Log.i(TAG, "Surveillance retour travail annulée.")
    }

    private fun loadLocalConfig(): ScheduleConfig {
        val enabled = prefs.getBoolean(KEY_ENABLED, true)

        val mEnabled = prefs.getBoolean(KEY_MORNING_ENABLED, true)
        val mStartHour = prefs.getInt(KEY_MORNING_START_HOUR, prefs.getInt(KEY_START_HOUR, 7))
        val mStartMinute = prefs.getInt(KEY_MORNING_START_MINUTE, prefs.getInt(KEY_START_MINUTE, 0))
        val mEndHour = prefs.getInt(KEY_MORNING_END_HOUR, prefs.getInt(KEY_END_HOUR, 9))
        val mEndMinute = prefs.getInt(KEY_MORNING_END_MINUTE, prefs.getInt(KEY_END_MINUTE, 30))

        val eEnabled = prefs.getBoolean(KEY_EVENING_ENABLED, true)
        val eStartHour = prefs.getInt(KEY_EVENING_START_HOUR, 17)
        val eStartMinute = prefs.getInt(KEY_EVENING_START_MINUTE, 0)
        val eEndHour = prefs.getInt(KEY_EVENING_END_HOUR, 19)
        val eEndMinute = prefs.getInt(KEY_EVENING_END_MINUTE, 30)

        val freq = prefs.getInt(KEY_FREQ, 3)
        val pausedUntil = prefs.getString(KEY_PAUSED_UNTIL, null)
        val returnCommuteUntil = prefs.getString(KEY_RETURN_COMMUTE_UNTIL, null)
        val daysString = prefs.getString(KEY_ACTIVE_DAYS, "0,1,2,3,4") ?: "0,1,2,3,4"

        val days = daysString.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(0, 1, 2, 3, 4) }

        return ScheduleConfig(
            enabled = enabled,
            morningEnabled = mEnabled,
            morningStartHour = mStartHour,
            morningStartMinute = mStartMinute,
            morningEndHour = mEndHour,
            morningEndMinute = mEndMinute,
            eveningEnabled = eEnabled,
            eveningStartHour = eStartHour,
            eveningStartMinute = eStartMinute,
            eveningEndHour = eEndHour,
            eveningEndMinute = eEndMinute,
            activeDays = days,
            frequencyMinutes = freq,
            pausedUntil = pausedUntil,
            returnCommuteUntil = returnCommuteUntil
        )
    }

    private fun saveLocalConfig(config: ScheduleConfig) {
        prefs.edit().apply {
            putBoolean(KEY_ENABLED, config.enabled)
            putBoolean(KEY_MORNING_ENABLED, config.morningEnabled)
            putInt(KEY_MORNING_START_HOUR, config.morningStartHour)
            putInt(KEY_MORNING_START_MINUTE, config.morningStartMinute)
            putInt(KEY_MORNING_END_HOUR, config.morningEndHour)
            putInt(KEY_MORNING_END_MINUTE, config.morningEndMinute)
            // Backwards compatibility
            putInt(KEY_START_HOUR, config.morningStartHour)
            putInt(KEY_START_MINUTE, config.morningStartMinute)
            putInt(KEY_END_HOUR, config.morningEndHour)
            putInt(KEY_END_MINUTE, config.morningEndMinute)

            putBoolean(KEY_EVENING_ENABLED, config.eveningEnabled)
            putInt(KEY_EVENING_START_HOUR, config.eveningStartHour)
            putInt(KEY_EVENING_START_MINUTE, config.eveningStartMinute)
            putInt(KEY_EVENING_END_HOUR, config.eveningEndHour)
            putInt(KEY_EVENING_END_MINUTE, config.eveningEndMinute)

            putInt(KEY_FREQ, config.frequencyMinutes)
            putString(KEY_PAUSED_UNTIL, config.pausedUntil)
            putString(KEY_RETURN_COMMUTE_UNTIL, config.returnCommuteUntil)
            putString(KEY_ACTIVE_DAYS, config.activeDays.joinToString(","))
            apply()
        }
    }

    companion object {
        private const val TAG = "ScheduleRepository"
        private const val PREFS_NAME = "null_track_schedule_prefs"
        private const val SETTINGS_COLLECTION = "settings"
        private const val SCHEDULE_DOC = "monitoring_schedule"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"

        private const val KEY_MORNING_ENABLED = "morning_enabled"
        private const val KEY_MORNING_START_HOUR = "morning_start_hour"
        private const val KEY_MORNING_START_MINUTE = "morning_start_minute"
        private const val KEY_MORNING_END_HOUR = "morning_end_hour"
        private const val KEY_MORNING_END_MINUTE = "morning_end_minute"

        private const val KEY_EVENING_ENABLED = "evening_enabled"
        private const val KEY_EVENING_START_HOUR = "evening_start_hour"
        private const val KEY_EVENING_START_MINUTE = "evening_start_minute"
        private const val KEY_EVENING_END_HOUR = "evening_end_hour"
        private const val KEY_EVENING_END_MINUTE = "evening_end_minute"

        private const val KEY_FREQ = "frequency"
        private const val KEY_PAUSED_UNTIL = "paused_until"
        private const val KEY_RETURN_COMMUTE_UNTIL = "return_commute_until"
        private const val KEY_ACTIVE_DAYS = "active_days"

        @Volatile
        private var instance: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return instance ?: synchronized(this) {
                instance ?: ScheduleRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
