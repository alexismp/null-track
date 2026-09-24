package com.nulltrack.data

enum class DepartureStatus {
    ON_TIME,
    DELAYED,
    CANCELLED
}

data class TrainDeparture(
    val id: String,
    val missionCode: String,
    val destination: String,
    val direction: String,
    val aimedTime: String,
    val expectedTime: String,
    val platform: String,
    val status: DepartureStatus,
    val statusLabel: String,
    val delayMinutes: Int,
    val isCancelled: Boolean
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): TrainDeparture {
            val id = (map["id"] as? String) ?: ""
            val mission = (map["mission_code"] as? String) ?: ""
            val destination = (map["destination"] as? String) ?: "Paris-Montparnasse"
            val direction = (map["direction"] as? String) ?: "Paris-Montparnasse"
            val aimedTime = (map["aimed_time"] as? String) ?: "--:--"
            val expectedTime = (map["expected_time"] as? String) ?: aimedTime
            val platform = (map["platform"] as? String) ?: ""
            val isCancelled = (map["is_cancelled"] as? Boolean) ?: false
            val delayMinutes = (map["delay_minutes"] as? Number)?.toInt() ?: 0
            val statusLabel = (map["status_label"] as? String) ?: "À l'heure"

            val statusStr = (map["status"] as? String)?.uppercase() ?: "ON_TIME"
            val status = when {
                isCancelled || statusStr == "CANCELLED" -> DepartureStatus.CANCELLED
                delayMinutes >= 2 || statusStr == "DELAYED" -> DepartureStatus.DELAYED
                else -> DepartureStatus.ON_TIME
            }

            return TrainDeparture(
                id = id,
                missionCode = mission,
                destination = destination,
                direction = direction,
                aimedTime = aimedTime,
                expectedTime = expectedTime,
                platform = platform,
                status = status,
                statusLabel = statusLabel,
                delayMinutes = delayMinutes,
                isCancelled = isCancelled
            )
        }
    }
}
