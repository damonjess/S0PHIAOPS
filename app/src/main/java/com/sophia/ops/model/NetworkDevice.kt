package com.sophia.ops.model

data class NetworkDevice(
    val id: String,
    val name: String,
    val address: String,
    val vendor: String? = null,
    val type: DeviceType,
    val signal: Int,
    val favourite: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis()
)

enum class DeviceType {
    WIFI,
    BLUETOOTH
}
