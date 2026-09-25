package com.example.smart_expense_tracker



import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ManualEntryDialog(
    context: android.content.Context,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add cash expense", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    label = { Text("Paid to / for what?") },
                    placeholder = { Text("e.g. Vegetable vendor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount != null && amount > 0 && descriptionText.isNotBlank()) {
                            scope.launch {
                                val db = AppDatabase.getDatabase(context)

                                val override = db.merchantOverrideDao().getOverride(descriptionText.lowercase())
                                val result = if (override != null) {
                                    CategoryResult(override, "Miscellaneous")
                                } else {
                                    Categorizer.categorize(descriptionText)
                                }

                                val dateStr = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
                                val refId = "MANUAL-${System.currentTimeMillis()}"

                                val transaction = Transaction(
                                    amount = amount,
                                    recipient = descriptionText.trim(),
                                    date = dateStr,
                                    refNumber = refId,
                                    rawText = "Manual entry",
                                    source = "manual",
                                    category = result.category,
                                    subcategory = result.subcategory
                                )

                                db.transactionDao().insert(transaction)
                                onSaved()
                            }
                        }
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}