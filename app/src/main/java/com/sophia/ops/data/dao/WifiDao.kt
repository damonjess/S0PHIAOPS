package com.sophia.ops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sophia.ops.data.entities.WifiNetwork
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(
        network: WifiNetwork
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(networks: List<WifiNetwork>)

    @Query(
        "SELECT * FROM wifi_networks ORDER BY timestamp DESC"
    )
    fun getAll(): Flow<List<WifiNetwork>>

    @Query("SELECT * FROM wifi_networks WHERE bssid = :bssid ORDER BY timestamp DESC LIMIT 1")
    fun getNetworkByBssidFlow(bssid: String): Flow<WifiNetwork?>

    @Query("SELECT COUNT(*) FROM wifi_networks")
    fun getCount(): Flow<Int>

    @Query("DELETE FROM wifi_networks")
    suspend fun deleteAllNetworks()
}
