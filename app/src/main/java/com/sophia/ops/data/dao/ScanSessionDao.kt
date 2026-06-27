package com.sophia.ops.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sophia.ops.data.entities.ScanSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {

    @Insert
    suspend fun insert(
        session: ScanSession
    )

    @Query(
        "SELECT * FROM scan_sessions ORDER BY timestamp DESC"
    )
    fun getAll():
        Flow<List<ScanSession>>

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT COUNT(*) FROM scan_sessions")
    fun getCount(): Flow<Int>

    @Query("SELECT * FROM scan_sessions WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getSessionsSince(since: Long): Flow<List<ScanSession>>
}
