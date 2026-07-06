package com.sophia.ops.model

data class NetworkDevice(
    val name: String,
    val macAddress: String,
    val angle: Float,            // 0f to 360f
    val distancePercent: Float,  // 0f to 100f (clamped radius)
    val dBm: Int,                // Signal strength (e.g., -65)
    val isBluetooth: Boolean     // True for BT, False for Wi-Fi
)
