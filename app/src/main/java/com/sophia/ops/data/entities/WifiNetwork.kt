package com.sophia.ops.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wifi_networks")
data class WifiNetwork(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ssid: String,
    val bssid: String,
    val signal: Int,
    val security: String,
    val riskScore: Int,
    val timestamp: Long
)
