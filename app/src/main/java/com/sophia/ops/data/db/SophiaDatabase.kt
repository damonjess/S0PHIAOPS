package com.sophia.ops.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sophia.ops.data.dao.BluetoothDao
import com.sophia.ops.data.dao.WifiDao
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork

@Database(
    entities = [
        WifiNetwork::class,
        BluetoothDeviceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SophiaDatabase : RoomDatabase() {

    abstract fun wifiDao(): WifiDao
    abstract fun bluetoothDao(): BluetoothDao
}
