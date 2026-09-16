package com.sophia.ops.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_networks", indices = [Index(value = ["bssid"], unique = true)])
data class WifiNetwork(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ssid: String,
    val bssid: String,
    val signal: Int,
    val security: String,
    val riskScore: Int,
    val timestamp: Long,
    val angularOffset: Float = 0f
)
