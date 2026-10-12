package com.example.smart_expense_tracker.healthscore

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores one snapshot of the computed health score at a point in time,
 * so we can plot "score over time" (Section 5.5 of the feature spec).
 * A new row is inserted every time the score is (re)calculated on the
 * dashboard — NOT on every loan-simulator slider move, since those are
 * hypothetical and shouldn't pollute real history.
 */
@Entity(tableName = "health_score_history")
data class HealthScoreHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val score: Int,
    val riskLevel: String,   // stored as String ("LOW"/"MEDIUM"/"HIGH") for simple Room storage
    val timestamp: Long = System.currentTimeMillis()
)
