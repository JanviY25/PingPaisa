package com.example.smart_expense_tracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecurringItemDao {

    @Insert
    suspend fun insert(item: RecurringItem)

    @Delete
    suspend fun delete(item: RecurringItem)

    @Query("SELECT * FROM recurring_items ORDER BY dayOfMonth ASC")
    suspend fun getAll(): List<RecurringItem>
}