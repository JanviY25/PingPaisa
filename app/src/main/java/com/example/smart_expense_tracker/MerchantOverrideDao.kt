package com.example.smart_expense_tracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MerchantOverrideDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setOverride(override: MerchantOverride)

    @Query("SELECT category FROM merchant_overrides WHERE recipient = :recipient")
    suspend fun getOverride(recipient: String): String?
}