package com.example.smart_expense_tracker.healthscore

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HealthScoreHistoryDao {
    @Insert
    suspend fun insert(entry: HealthScoreHistory)

    @Query("SELECT * FROM health_score_history ORDER BY timestamp ASC")
    suspend fun getAll(): List<HealthScoreHistory>
}