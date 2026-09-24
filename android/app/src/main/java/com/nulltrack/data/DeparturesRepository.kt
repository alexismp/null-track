package com.nulltrack.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

import android.content.Intent

class DeparturesRepository private constructor(private val context: Context) {

    private val _departures = MutableStateFlow<List<TrainDeparture>>(emptyList())
    val departures: StateFlow<List<TrainDeparture>> = _departures.asStateFlow()

    private val _lastUpdated = MutableStateFlow<String?>("Chargement...")
    val lastUpdated: StateFlow<String?> = _lastUpdated.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var firestoreListener: ListenerRegistration? = null

    init {
        initFirestoreSync()
    }

    private fun initFirestoreSync() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection(COLLECTION_LIVE_STATUS).document(DOC_DEPARTURES)

            firestoreListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Erreur écoute départs temps réel: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val rawDepartures = snapshot.get("departures") as? List<*>
                    val parsed = rawDepartures?.mapNotNull { item ->
                        (item as? Map<String, Any?>)?.let { TrainDeparture.fromMap(it) }
                    } ?: emptyList()

                    _departures.value = parsed

                    val rawUpdated = snapshot.getString("updated_at")
                    _lastUpdated.value = formatIsoToTime(rawUpdated)
                    Log.d(TAG, "${parsed.size} prochains départs synchronisés depuis Firestore.")
                    context.sendBroadcast(Intent("com.nulltrack.widget.ACTION_REFRESH").setPackage(context.packageName))
                } else {
                    // Si aucun document distant n'existe encore, données d'exemple pour Meudon
                    if (_departures.value.isEmpty()) {
                        _departures.value = getFallbackDepartures()
                        _lastUpdated.value = "Mode hors-ligne"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore non disponible pour départs temps réel: ${e.message}")
            if (_departures.value.isEmpty()) {
                _departures.value = getFallbackDepartures()
                _lastUpdated.value = "Mode local"
            }
        }
    }

    fun hasDisruptions(minDelayMinutes: Int = 5): Boolean {
        return _departures.value.any {
            it.isCancelled || it.delayMinutes >= minDelayMinutes ||
            it.status == DepartureStatus.CANCELLED || it.status == DepartureStatus.DELAYED
        }
    }

    fun getDisruptionSummary(minDelayMinutes: Int = 5): String? {
        val cancelled = _departures.value.filter { it.isCancelled || it.status == DepartureStatus.CANCELLED }
        if (cancelled.isNotEmpty()) {
            val t = cancelled.first()
            return "Train ${t.missionCode} ${t.aimedTime} supprimé"
        }
        val delayed = _departures.value.filter { !it.isCancelled && it.delayMinutes >= minDelayMinutes }
        if (delayed.isNotEmpty()) {
            val t = delayed.first()
            return "Train ${t.missionCode} ${t.aimedTime} retardé (+${t.delayMinutes}m)"
        }
        return null
    }

    fun refresh() {
        _isLoading.value = true
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection(COLLECTION_LIVE_STATUS).document(DOC_DEPARTURES).get()
                .addOnSuccessListener { snapshot ->
                    _isLoading.value = false
                    if (snapshot.exists()) {
                        val rawDepartures = snapshot.get("departures") as? List<*>
                        val parsed = rawDepartures?.mapNotNull { item ->
                            (item as? Map<String, Any?>)?.let { TrainDeparture.fromMap(it) }
                        } ?: emptyList()
                        _departures.value = parsed
                        _lastUpdated.value = formatIsoToTime(snapshot.getString("updated_at"))
                    }
                }
                .addOnFailureListener {
                    _isLoading.value = false
                }
        } catch (e: Exception) {
            _isLoading.value = false
        }
    }

    private fun formatIsoToTime(isoStr: String?): String {
        if (isoStr.isNullOrBlank()) {
            val nowFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)
            return nowFormat.format(Date())
        }
        return try {
            val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = isoParser.parse(isoStr.substring(0, 19))
            val localFormat = SimpleDateFormat("HH:mm", Locale.FRANCE).apply {
                timeZone = TimeZone.getTimeZone("Europe/Paris")
            }
            localFormat.format(date ?: Date())
        } catch (e: Exception) {
            isoStr.takeLast(8).take(5)
        }
    }

    private fun getFallbackDepartures(): List<TrainDeparture> {
        return listOf(
            TrainDeparture(
                id = "mock_1",
                missionCode = "PORO",
                destination = "Paris Montparnasse",
                direction = "Paris-Montparnasse",
                aimedTime = "08:12",
                expectedTime = "08:12",
                platform = "2B",
                status = DepartureStatus.CANCELLED,
                statusLabel = "Supprimé",
                delayMinutes = 0,
                isCancelled = true
            ),
            TrainDeparture(
                id = "mock_2",
                missionCode = "POMA",
                destination = "Paris Montparnasse",
                direction = "Paris-Montparnasse",
                aimedTime = "08:27",
                expectedTime = "08:33",
                platform = "2B",
                status = DepartureStatus.DELAYED,
                statusLabel = "+6 min",
                delayMinutes = 6,
                isCancelled = false
            ),
            TrainDeparture(
                id = "mock_3",
                missionCode = "PORO",
                destination = "Paris Montparnasse",
                direction = "Paris-Montparnasse",
                aimedTime = "08:42",
                expectedTime = "08:42",
                platform = "2B",
                status = DepartureStatus.ON_TIME,
                statusLabel = "À l'heure",
                delayMinutes = 0,
                isCancelled = false
            ),
            TrainDeparture(
                id = "mock_4",
                missionCode = "ROPO",
                destination = "Rambouillet",
                direction = "Banlieue",
                aimedTime = "08:45",
                expectedTime = "08:45",
                platform = "1B",
                status = DepartureStatus.ON_TIME,
                statusLabel = "À l'heure",
                delayMinutes = 0,
                isCancelled = false
            )
        )
    }

    companion object {
        private const val TAG = "DeparturesRepository"
        private const val COLLECTION_LIVE_STATUS = "live_status"
        private const val DOC_DEPARTURES = "departures_meudon"

        @Volatile
        private var instance: DeparturesRepository? = null

        fun getInstance(context: Context): DeparturesRepository {
            return instance ?: synchronized(this) {
                instance ?: DeparturesRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
