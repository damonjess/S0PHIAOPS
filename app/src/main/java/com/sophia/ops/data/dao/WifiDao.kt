package com.sophia.ops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sophia.ops.data.entities.WifiNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiDao {

    @Insert
    suspend fun insert(
        network: WifiNetwork
    )

    @Query(
        "SELECT * FROM wifi_networks ORDER BY timestamp DESC"
    )
    fun getAll(): Flow<List<WifiNetwork>>
}
