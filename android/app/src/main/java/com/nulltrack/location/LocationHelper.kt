package com.nulltrack.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Calendar

enum class CommuteDirection(val code: String, val label: String) {
    TO_PARIS("TO_PARIS", "Meudon ➔ Paris"),
    TO_MEUDON("TO_MEUDON", "Paris ➔ Meudon")
}

data class CommuteLocationDetection(
    val direction: CommuteDirection,
    val locationLabel: String,
    val distanceToMeudonKm: Float?,
    val isEstimated: Boolean
)

object LocationHelper {

    // Coordonnées de la gare de Meudon (Ligne N)
    const val MEUDON_LAT = 48.81423
    const val MEUDON_LNG = 2.23846

    // Coordonnées de Paris (Montparnasse)
    const val PARIS_MONTPARNASSE_LAT = 48.8412
    const val PARIS_MONTPARNASSE_LNG = 2.3205

    // Rayon de proximité avec la gare de départ / domicile : 4 km
    private const val MEUDON_PROXIMITY_RADIUS_METERS = 4000f

    /**
     * Vérifie si les permissions de localisation sont accordées.
     */
    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Détermine la direction du trajet selon la position géographique réelle.
     * - Si proche de Meudon (< 4km ou plus proche de Meudon que de Paris) => Sens Banlieue ➔ Paris.
     * - Si dans Paris / au travail => Sens Paris ➔ Banlieue (Paris ➔ Meudon).
     * - Si localisation désactivée => bascule intelligente selon l'heure de la journée.
     */
    fun detectCommuteDirection(context: Context): CommuteLocationDetection {
        if (hasLocationPermission(context)) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )

                var bestLocation: Location? = null
                for (provider in providers) {
                    try {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            if (bestLocation == null || loc.time > bestLocation.time) {
                                bestLocation = loc
                            }
                        }
                    } catch (ignored: SecurityException) {
                    }
                }

                if (bestLocation != null) {
                    val meudonDist = FloatArray(1)
                    Location.distanceBetween(
                        bestLocation.latitude,
                        bestLocation.longitude,
                        MEUDON_LAT,
                        MEUDON_LNG,
                        meudonDist
                    )

                    val parisDist = FloatArray(1)
                    Location.distanceBetween(
                        bestLocation.latitude,
                        bestLocation.longitude,
                        PARIS_MONTPARNASSE_LAT,
                        PARIS_MONTPARNASSE_LNG,
                        parisDist
                    )

                    val distanceToMeudonMeters = meudonDist[0]
                    val distanceToParisMeters = parisDist[0]
                    val kmToMeudon = distanceToMeudonMeters / 1000f

                    return if (distanceToMeudonMeters <= MEUDON_PROXIMITY_RADIUS_METERS || distanceToMeudonMeters < distanceToParisMeters) {
                        CommuteLocationDetection(
                            direction = CommuteDirection.TO_PARIS,
                            locationLabel = "Proche de Meudon (${String.format(java.util.Locale.FRANCE, "%.1f", kmToMeudon)} km)",
                            distanceToMeudonKm = kmToMeudon,
                            isEstimated = false
                        )
                    } else {
                        CommuteLocationDetection(
                            direction = CommuteDirection.TO_MEUDON,
                            locationLabel = "À Paris / Travail",
                            distanceToMeudonKm = kmToMeudon,
                            isEstimated = false
                        )
                    }
                }
            }
        }

        // Si localisation indisponible, estimation basée sur l'heure (matin vs après-midi/soir)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (hour < 13) {
            CommuteLocationDetection(
                direction = CommuteDirection.TO_PARIS,
                locationLabel = "Estimation matin : Domicile ➔ Paris",
                distanceToMeudonKm = null,
                isEstimated = true
            )
        } else {
            CommuteLocationDetection(
                direction = CommuteDirection.TO_MEUDON,
                locationLabel = "Estimation soir : Travail ➔ Meudon",
                distanceToMeudonKm = null,
                isEstimated = true
            )
        }
    }
}
