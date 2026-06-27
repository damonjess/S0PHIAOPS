package com.sophia.ops.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bluetooth_devices")
data class BluetoothDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val address: String,
    val deviceType: Int,
    val firstSeen: Long,
    val lastSeen: Long,
    val riskScore: Int,
    val timesSeen: Int = 1
)
