package com.sophia.ops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sophia.ops.data.entities.BluetoothDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BluetoothDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: BluetoothDeviceEntity)

    @Query("SELECT * FROM bluetooth_devices ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<BluetoothDeviceEntity>>

    @Query("UPDATE bluetooth_devices SET name = :name WHERE id = :id")
    suspend fun updateName(id: Int, name: String)

    @Query("SELECT * FROM bluetooth_devices WHERE address = :address LIMIT 1")
    suspend fun getDeviceByAddress(address: String): BluetoothDeviceEntity?

    @Update
    suspend fun updateDevice(device: BluetoothDeviceEntity)

    @Query("DELETE FROM bluetooth_devices")
    suspend fun deleteAllDevices()
}
