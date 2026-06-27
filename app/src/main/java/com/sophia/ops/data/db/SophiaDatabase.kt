package com.sophia.ops.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sophia.ops.data.dao.BluetoothDao
import com.sophia.ops.data.dao.WifiDao
import com.sophia.ops.data.dao.ScanSessionDao
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import com.sophia.ops.data.entities.WifiNetwork
import com.sophia.ops.data.entities.ScanSession

@Database(
    entities = [
        WifiNetwork::class,
        BluetoothDeviceEntity::class,
        ScanSession::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SophiaDatabase : RoomDatabase() {

    abstract fun wifiDao(): WifiDao
    abstract fun bluetoothDao(): BluetoothDao
    abstract fun scanSessionDao(): ScanSessionDao
}
