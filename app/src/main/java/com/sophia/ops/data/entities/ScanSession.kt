package com.sophia.ops.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_sessions")
data class ScanSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val wifiCount: Int,
    val bluetoothCount: Int,
    val threatScore: Int
)
