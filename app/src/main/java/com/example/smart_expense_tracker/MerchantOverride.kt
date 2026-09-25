package com.example.smart_expense_tracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_overrides")
data class MerchantOverride(
    @PrimaryKey
    val recipient: String,   // stored lowercase, exact match
    val category: String
)