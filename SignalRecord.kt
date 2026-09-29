package com.tradesignal.ai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One saved analysis. Stored only on the device; nothing is uploaded. */
@Entity(tableName = "signals")
data class SignalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val signal: String,        // UP / DOWN / WAIT
    val confidence: Int,       // 0 when WAIT
    val structure: String,
    val momentum: String,
    val recent: String,
    val levels: String,
    val factors: String,
    val waitReason: String?,
    val outcome: String? = null // WIN / LOSS / NOT_TRADED / null = not recorded
)
