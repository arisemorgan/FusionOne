package com.fusionone.app.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Update
    suspend fun update(entity: ScanHistoryEntity)

    @Delete
    suspend fun delete(entity: ScanHistoryEntity)

    @Query("DELETE FROM scan_history")
    suspend fun clearAll()

    @Query("SELECT * FROM scan_history ORDER BY scannedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE verdict = :verdict ORDER BY scannedAtEpochMillis DESC")
    fun observeByVerdict(verdict: String): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE displayDomain LIKE '%' || :query || '%' ORDER BY scannedAtEpochMillis DESC")
    fun search(query: String): Flow<List<ScanHistoryEntity>>
}
