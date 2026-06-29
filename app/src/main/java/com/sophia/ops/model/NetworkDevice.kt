package com.sophia.ops.model

data class NetworkDevice(
    val id: String,
    val name: String,
    val address: String,
    val vendor: String? = null,
    val type: DeviceType,
    val signal: Int,
    val favourite: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val firstSeen: Long = lastSeen,
    val riskScore: Int = 0,
    val timesSeen: Int = 1,
    val threatScore: Float = riskScore.toFloat(),
    val radarAngle: Float = 0f,
    val ipAddress: String = "Unknown",
    val status: String = "Active"
)

enum class DeviceType {
    WIFI,
    BLUETOOTH
}
