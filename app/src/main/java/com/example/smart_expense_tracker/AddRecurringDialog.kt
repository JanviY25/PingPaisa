package com.example.smart_expense_tracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecurringDialog(
    context: android.content.Context,
    prefillName: String = "",
    prefillAmount: Double? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var nameText by remember { mutableStateOf(prefillName) }
    var amountText by remember { mutableStateOf(prefillAmount?.toString() ?: "") }
    var dayText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EMI") }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Add recurring payment", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = nameText, onValueChange = { nameText = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Bajaj Finserv EMI") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = dayText, onValueChange = { dayText = it },
                    label = { Text("Day of month due (1-31)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))

                Text("Type", fontSize = 13.sp)
                Row {
                    listOf("EMI", "SIP", "Other").forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val amount = amountText.toDoubleOrNull()
                        val day = dayText.toIntOrNull()
                        if (nameText.isNotBlank() && amount != null && amount > 0 && day != null && day in 1..31) {
                            scope.launch {
                                val db = AppDatabase.getDatabase(context)
                                db.recurringItemDao().insert(
                                    RecurringItem(
                                        name = nameText.trim(),
                                        amount = amount,
                                        category = if (selectedType == "EMI" || selectedType == "SIP") "Financial Services" else "Miscellaneous",
                                        dayOfMonth = day,
                                        type = selectedType
                                    )
                                )
                                onSaved()
                            }
                        }
                    }) { Text("Save") }
                }
            }
        }
    }
}