package com.tradesignal.ai.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Insert
    suspend fun insert(record: SignalRecord): Long

    @Query("SELECT * FROM signals ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SignalRecord>>

    @Query("SELECT * FROM signals ORDER BY timestamp DESC")
    suspend fun getAll(): List<SignalRecord>

    @Query("UPDATE signals SET outcome = :outcome WHERE id = :id")
    suspend fun setOutcome(id: Long, outcome: String)

    @Query("DELETE FROM signals")
    suspend fun clearAll()
}
