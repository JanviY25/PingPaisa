package com.example.smart_expense_tracker

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["refNumber"], unique = true)]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val amount: Double,
    val recipient: String,
    val date: String,
    val refNumber: String,
    val rawText: String,
    val source: String = "SMS",
    val category: String = "Uncategorized",
    val subcategory: String = "Miscellaneous"
)