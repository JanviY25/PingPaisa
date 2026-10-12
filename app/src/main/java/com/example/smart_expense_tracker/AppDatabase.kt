package com.example.smart_expense_tracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smart_expense_tracker.healthscore.HealthScoreHistory
import com.example.smart_expense_tracker.healthscore.HealthScoreHistoryDao

@Database(
    entities = [Transaction::class, MerchantOverride::class, RecurringItem::class, HealthScoreHistory::class],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun merchantOverrideDao(): MerchantOverrideDao
    abstract fun recurringItemDao(): RecurringItemDao
    abstract fun healthScoreHistoryDao(): HealthScoreHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}