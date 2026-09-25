package com.example.smart_expense_tracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TransactionDao {
    @Query("UPDATE transactions SET category = :category WHERE recipient = :recipient")
    suspend fun updateCategoryForRecipient(recipient: String, category: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction)

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE refNumber = :ref)")
    suspend fun existsByRef(ref: String): Boolean

    @Query("SELECT * FROM transactions ORDER BY id DESC")
    suspend fun getAll(): List<Transaction>
}