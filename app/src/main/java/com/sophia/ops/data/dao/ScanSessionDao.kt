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
}
