package com.sophia.ops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BluetoothDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: BluetoothDeviceEntity)

    @Query("SELECT * FROM bluetooth_devices ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<BluetoothDeviceEntity>>
}
