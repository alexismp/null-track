package com.nulltrack.data

data class TrainAlert(
    val id: String,
    val missionCode: String,
    val departureTime: String,
    val stopName: String,
    val destination: String,
    val status: String = "ANNULÉ",
    val receivedAtTimestamp: Long = System.currentTimeMillis()
)
