package com.sophia.ops.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sophia.ops.data.dao.WifiDao
import com.sophia.ops.data.entities.WifiNetwork

@Database(
    entities = [WifiNetwork::class],
    version = 1
)
abstract class SophiaDatabase : RoomDatabase() {

    abstract fun wifiDao(): WifiDao
}
