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

    @Query("UPDATE bluetooth_devices SET name = :name WHERE address = :address")
    suspend fun updateNameByAddress(address: String, name: String)

    @Query("UPDATE bluetooth_devices SET favourite = :state WHERE address = :address")
    suspend fun updateFavourite(address: String, state: Boolean)

    @Query("UPDATE bluetooth_devices SET nickname = :nickname WHERE address = :address")
    suspend fun updateNickname(address: String, nickname: String?)

    @Query("UPDATE bluetooth_devices SET notes = :notes WHERE address = :address")
    suspend fun updateNotes(address: String, notes: String?)

    @Query("UPDATE bluetooth_devices SET ignored = :ignored WHERE address = :address")
    suspend fun updateIgnored(address: String, ignored: Boolean)

    @Query("SELECT * FROM bluetooth_devices WHERE address = :address LIMIT 1")
    suspend fun getDeviceByAddress(address: String): BluetoothDeviceEntity?

    @Query("SELECT * FROM bluetooth_devices WHERE address = :address LIMIT 1")
    fun getDeviceByAddressFlow(address: String): Flow<BluetoothDeviceEntity?>

    @Update
    suspend fun updateDevice(device: BluetoothDeviceEntity)

    @Query("DELETE FROM bluetooth_devices")
    suspend fun deleteAllDevices()
}
