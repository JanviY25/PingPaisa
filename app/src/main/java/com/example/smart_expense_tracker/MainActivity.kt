package com.example.smart_expense_tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import android.util.Log
import kotlinx.coroutines.launch
import com.example.smart_expense_tracker.healthscore.HealthScoreScreen
import com.example.smart_expense_tracker.healthscore.LoanSimulatorScreen
import com.example.smart_expense_tracker.healthscore.ScoreCalculator


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Categorizer.init(applicationContext)

        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                ExpenseTrackerScreen(applicationContext)
            }
        }
    }
}

val pastelColors = listOf(
    Color(0xFFFFD3B0), Color(0xFFB5EAD7), Color(0xFFC7CEEA),
    Color(0xFFFFDAC1), Color(0xFFE2F0CB), Color(0xFFFFB7B2),
    Color(0xFFFFE4E1), Color(0xFFD4A5A5), Color(0xFFA0CED9),
)

fun colorForCategory(category: String, allCategories: List<String>): Color {
    val index = allCategories.indexOf(category).let { if (it < 0) 0 else it }
    return pastelColors[index % pastelColors.size]
}

fun emojiForCategory(category: String): String = when (category.lowercase()) {
    "food & dining" -> "🍔"
    "grocery" -> "🛒"
    "travel" -> "🚕"
    "shopping" -> "🛍️"
    "uncategorized" -> "❓"
    else -> "💸"
}

sealed class ListRow {
    data class Header(val category: String, val total: Double) : ListRow()
    data class Item(val txn: Transaction) : ListRow()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseTrackerScreen(context: android.content.Context) {
    var transactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDetected by remember { mutableStateOf<DetectedRecurring?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var recurringItems by remember { mutableStateOf<List<RecurringItem>>(emptyList()) }
    var detectedRecurring by remember { mutableStateOf<List<DetectedRecurring>>(emptyList()) }
    var showAddRecurring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val db = AppDatabase.getDatabase(context)
        transactions = db.transactionDao().getAll()
        recurringItems = db.recurringItemDao().getAll()
        detectedRecurring = RecurringDetector.detect(transactions)
    }

    LaunchedEffect(Unit) { refresh() }

    val totalSpent = transactions.sumOf { it.amount }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF6EC)).padding(20.dp)) {
                when (selectedTab) {
                    3 -> {
                        Text("HEALTH SCORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7FA99B))
                        Spacer(Modifier.height(4.dp))
                        Text("Your financial score", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    4 -> {
                        Text("LOAN PLANNING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7FA99B))
                        Spacer(Modifier.height(4.dp))
                        Text("Simulate a loan", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    else -> {
                        Text("Hey there! 👋", fontSize = 16.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        Text("₹${"%.2f".format(totalSpent)}", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Text("total spent so far", fontSize = 14.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("List") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Insights") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Recurring") })
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Health") })
                    Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Loan") })
                }
            }
        },
        floatingActionButton = {
            // Hidden on Health (3) and Loan (4) tabs — the add/refresh
            // actions aren't relevant there, they're for transaction data.
            if (selectedTab != 3 && selectedTab != 4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedTab == 2) {
                                showAddRecurring = true
                            } else {
                                showManualEntry = true
                            }
                        },
                        containerColor = Color(0xFFFFB7B2)
                    ) { Text("➕", fontSize = 20.sp) }
                    Spacer(Modifier.height(12.dp))
                    FloatingActionButton(
                        onClick = { scope.launch { refresh() } },
                        containerColor = Color(0xFFC7CEEA)
                    ) { Text("🔄", fontSize = 20.sp) }
                }
            }
        }
    ) { padding ->
        val testRawFactors = ScoreCalculator.RawFactors(
            income = 40000.0,
            expense = totalSpent,
            totalEmiAmount = recurringItems.filter { it.type == "EMI" }.sumOf { it.amount },
            monthlyTotals = listOf(totalSpent),
            recurringOccurrences = detectedRecurring.sumOf { it.occurrences },
            recurringExpectedMonths = if (detectedRecurring.isNotEmpty()) 3 else 0
        )
        val testResult = ScoreCalculator.calculate(testRawFactors)

        if (selectedTab == 3) {
            HealthScoreScreen(
                result = testResult,
                aiInsight = "This is placeholder text — real AI-generated insight coming soon.",
                modifier = Modifier.padding(padding)
            )
        } else if (selectedTab == 4) {
            LoanSimulatorScreen(
                baseline = testRawFactors,
                baselineScore = testResult.score,
                modifier = Modifier.padding(padding)
            )
        } else if (selectedTab == 2) {
            RecurringView(
                manualItems = recurringItems,
                detectedItems = detectedRecurring,
                padding = padding,
                onAddClick = { showAddRecurring = true },
                onDetectedClick = { selectedDetected = it }
            )
        } else if (transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌱", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No transactions yet", color = Color.Gray)
                }
            }
        } else if (selectedTab == 0) {
            val allCategoriesPresent = transactions.map { it.category }.distinct().sorted()

            val grouped = transactions.groupBy { it.category }
                .toList()
                .sortedByDescending { (_, txns) -> txns.sumOf { it.amount } }

            val rows = mutableListOf<ListRow>()
            grouped.forEach { (category, txns) ->
                rows.add(ListRow.Header(category, txns.sumOf { it.amount }))
                txns.forEach { rows.add(ListRow.Item(it)) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(rows) { row ->
                    when (row) {
                        is ListRow.Header -> CategoryHeader(row.category, row.total, allCategoriesPresent)
                        is ListRow.Item -> TransactionCard(row.txn, allCategoriesPresent, onClick = { selectedTransaction = row.txn })
                    }
                }
            }
        } else {
            InsightsView(transactions, padding)
        }
    }

    selectedTransaction?.let { txn ->
        CategoryPickerSheet(
            recipient = txn.recipient,
            currentCategory = txn.category,
            onDismiss = { selectedTransaction = null },
            onCategorySelected = { newCategory ->
                scope.launch {
                    val db = AppDatabase.getDatabase(context)
                    db.merchantOverrideDao().setOverride(
                        MerchantOverride(recipient = txn.recipient.lowercase(), category = newCategory)
                    )
                    db.transactionDao().updateCategoryForRecipient(txn.recipient, newCategory)
                    refresh()
                    selectedTransaction = null
                }
            }
        )
    }
    if (showManualEntry) {
        ManualEntryDialog(
            context = context,
            onDismiss = { showManualEntry = false },
            onSaved = {
                showManualEntry = false
                scope.launch { refresh() }
            }
        )
    }
    if (showAddRecurring) {
        AddRecurringDialog(
            context = context,
            onDismiss = { showAddRecurring = false },
            onSaved = {
                showAddRecurring = false
                scope.launch { refresh() }
            }
        )
    }
    selectedDetected?.let { detected ->
        AddRecurringDialog(
            context = context,
            prefillName = detected.name,
            prefillAmount = detected.averageAmount,
            onDismiss = { selectedDetected = null },
            onSaved = {
                selectedDetected = null
                scope.launch { refresh() }
            }
        )
    }
}

@Composable
fun CategoryHeader(category: String, total: Double, allCategories: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(colorForCategory(category, allCategories), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(emojiForCategory(category), fontSize = 16.sp)
            Spacer(Modifier.width(6.dp))
            Text(category, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
        }
        Text("₹${"%.0f".format(total)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun TransactionCard(txn: Transaction, allCategories: List<String>, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp))
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(colorForCategory(txn.category, allCategories), CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(emojiForCategory(txn.category), fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(txn.recipient, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(txn.subcategory, fontSize = 12.sp, color = Color.Gray)
        }
        Text("-₹${"%.0f".format(txn.amount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFE07A5F))
    }
}

@Composable
fun InsightsView(transactions: List<Transaction>, padding: PaddingValues) {
    val totals = transactions.groupBy { it.category }
        .map { (category, txns) -> category to txns.sumOf { it.amount } }
        .sortedByDescending { it.second }

    val allCategoriesPresent = totals.map { it.first }.sorted()
    val grandTotal = totals.sumOf { it.second }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        CategoryPieChart(totals, allCategoriesPresent)
        Spacer(Modifier.height(24.dp))

        totals.forEach { (category, amount) ->
            val percent = if (grandTotal > 0) (amount / grandTotal * 100) else 0.0
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(14.dp).background(colorForCategory(category, allCategoriesPresent), CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(category, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("₹${"%.0f".format(amount)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("${"%.0f".format(percent)}%", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CategoryPieChart(data: List<Pair<String, Double>>, allCategories: List<String>) {
    val total = data.sumOf { it.second }
    Canvas(modifier = Modifier.size(220.dp)) {
        var startAngle = -90f
        data.forEach { (category, amount) ->
            val sweep = if (total > 0) (amount / total * 360f).toFloat() else 0f
            drawArc(
                color = colorForCategory(category, allCategories),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true
            )
            startAngle += sweep
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    recipient: String,
    currentCategory: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    var customCategoryText by remember { mutableStateOf("") }
    val existingCategories = remember { Categorizer.getAllCategories() }

    fun submitCustom() {
        if (customCategoryText.isNotBlank()) {
            onCategorySelected(customCategoryText.trim())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Categorize", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(recipient, fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(16.dp))

            Text("Add a new category", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customCategoryText,
                    onValueChange = { customCategoryText = it },
                    placeholder = { Text("e.g. Friends & Family") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCustom() })
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submitCustom() }) { Text("Add") }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Or pick an existing category", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))

            existingCategories.forEach { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onCategorySelected(category) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(emojiForCategory(category), fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(category, fontSize = 15.sp)
                    if (category == currentCategory) {
                        Spacer(Modifier.weight(1f))
                        Text("✓", color = Color(0xFF6C9A8B))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable
fun RecurringView(
    manualItems: List<RecurringItem>,
    detectedItems: List<DetectedRecurring>,
    padding: PaddingValues,
    onAddClick: () -> Unit,
    onDetectedClick: (DetectedRecurring) -> Unit
) {
    val totalMonthly = manualItems.sumOf { it.amount } + detectedItems.sumOf { it.averageAmount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("₹${"%.0f".format(totalMonthly)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("estimated per month", fontSize = 12.sp, color = Color.Gray)
            }
            Button(onClick = onAddClick) { Text("+ Add") }
        }
        Spacer(Modifier.height(16.dp))

        if (manualItems.isEmpty() && detectedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📅", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No recurring payments yet", color = Color.Gray, fontSize = 13.sp)
                    Text("Add one manually, or wait for auto-detection", color = Color.LightGray, fontSize = 11.sp)
                }
            }
            return@Column
        }

        val manualByType = manualItems.groupBy { it.type }
            .toList()
            .sortedByDescending { (_, items) -> items.sumOf { it.amount } }

        if (detectedItems.isNotEmpty()) {
            val detectedTotal = detectedItems.sumOf { it.averageAmount }
            RecurringSectionHeader("Auto-detected", detectedTotal, Color(0xFFB5EAD7))
            detectedItems.forEach { item ->
                Box(modifier = Modifier.clickable { onDetectedClick(item) }) {
                    RecurringCard(
                        name = item.name,
                        amount = item.averageAmount,
                        subtitle = "Auto-detected · ${item.occurrences}x seen · tap to confirm type",
                        badgeColor = Color(0xFFB5EAD7)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        manualByType.forEach { (type, items) ->
            val typeTotal = items.sumOf { it.amount }
            val typeColor = when (type) {
                "EMI" -> Color(0xFFFFDAC1)
                "SIP" -> Color(0xFFC7CEEA)
                else -> Color(0xFFFFB7B2)
            }
            RecurringSectionHeader(type, typeTotal, typeColor)
            items.forEach { item ->
                RecurringCard(
                    name = item.name,
                    amount = item.amount,
                    subtitle = "due day ${item.dayOfMonth}",
                    badgeColor = typeColor
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun RecurringSectionHeader(title: String, total: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.DarkGray)
        }
        Text("₹${"%.0f".format(total)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun RecurringCard(name: String, amount: Double, subtitle: String, badgeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(badgeColor, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = Color.Gray)
        }
        Text("₹${"%.0f".format(amount)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}