package com.iranjan.hotspotscheduler.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionRecordDao {
    @Insert
    suspend fun insert(record: ExecutionRecordEntity): Long

    @Query("SELECT * FROM execution_records ORDER BY startedAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ExecutionRecordEntity>>

    @Query("SELECT * FROM execution_records WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): ExecutionRecordEntity?

    @Query("DELETE FROM execution_records WHERE startedAt < :cutoff")
    suspend fun pruneBefore(cutoff: Long)
}