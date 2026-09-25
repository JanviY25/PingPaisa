package com.example.smart_expense_tracker

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class PaymentNotificationListener : NotificationListenerService() {

    private val relevantPackages = setOf(
        "com.google.android.apps.messaging"
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        if (sbn.packageName !in relevantPackages) return

        val extras = sbn.notification.extras
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
        val fullText = bigText.ifBlank { text }

        Log.d("PaymentListener", "----- Notification captured -----")
        Log.d("PaymentListener", "Raw text: $fullText")

        val parsed = SmsParser.parse(fullText)
        if (parsed != null) {
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)

                // Check if user has manually corrected this recipient before
                val override = db.merchantOverrideDao().getOverride(parsed.recipient.lowercase())
                val result = if (override != null) {
                    CategoryResult(override, "Miscellaneous")
                } else {
                    Categorizer.categorize(parsed.recipient)
                }

                val finalTransaction = parsed.copy(category = result.category, subcategory = result.subcategory)
                db.transactionDao().insert(finalTransaction)

                Log.d("PaymentListener", "SAVED: ${finalTransaction.recipient} -> ${finalTransaction.category} / ${finalTransaction.subcategory}")
            }
        } else {
            Log.d("PaymentListener", "PARSED: no match (not a debit SMS)")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Categorizer.init(applicationContext)
        Log.d("PaymentListener", "Listener connected successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}