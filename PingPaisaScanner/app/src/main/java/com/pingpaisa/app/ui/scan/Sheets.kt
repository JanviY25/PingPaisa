@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pingpaisa.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.LineAssessment
import com.pingpaisa.app.finance.Priority
import com.pingpaisa.app.ui.theme.DropdownPicker
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.ui.theme.fieldColors
import com.pingpaisa.app.util.Money

private fun priceToText(p: Double?): String = when {
    p == null -> ""
    p % 1.0 == 0.0 -> p.toLong().toString()
    else -> "%.2f".format(p)
}

private fun sourceText(p: Product): String = when (p.priceSource) {
    PriceSource.OCR -> "Read from the image" + (p.priceConfidence?.let { " · ${(it * 100).toInt()}% confident" } ?: "")
    PriceSource.PAGE -> "Listed on the product page - may differ at checkout"
    PriceSource.SHARED_TEXT -> "Taken from the shared message"
    PriceSource.MANUAL -> "Entered by you"
    PriceSource.NONE -> "No price found - please enter it"
}

@Composable
fun EditProductSheet(
    product: Product, repo: CategoryRepository, line: LineAssessment?,
    onSave: (name: String, price: Double?, category: String, subcategory: String, priority: Priority) -> Unit,
    onRemove: () -> Unit, onDismiss: () -> Unit
) {
    var name by remember(product.id) { mutableStateOf(product.name) }
    var priceText by remember(product.id) { mutableStateOf(priceToText(product.price)) }
    var category by remember(product.id) { mutableStateOf(product.category) }
    var sub by remember(product.id) { mutableStateOf(product.subcategory) }
    var priority by remember(product.id) { mutableStateOf(product.priority) }

    val parsed = Money.parse(priceText)
    val priceError = priceText.isNotBlank() && (parsed == null || parsed <= 0.0 || parsed > 100_000_000.0)
    val subs = repo.subcategoriesOf(category)

    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = PP.Surface, contentColor = PP.TextHi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProductThumb(product.imagePath, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Edit product", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    product.message?.let { Text(it, fontSize = 12.sp, color = PP.TextLo) }
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                name, { name = it }, label = { Text(if (product.nameIsGuess) "Product name (please confirm)" else "Product name") },
                singleLine = true, colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                priceText, { v -> priceText = v.filter { it.isDigit() || it == '.' }.take(12) },
                label = { Text("Price") }, prefix = { Text("₹ ") }, singleLine = true, isError = priceError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(),
                supportingText = {
                    Text(
                        if (priceError) "Enter a valid amount"
                        else (parsed?.let { Money.format(it) + " · " } ?: "") + sourceText(product)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            product.priceNote?.let { Text(it, fontSize = 12.sp, color = PP.Accent, modifier = Modifier.padding(top = 2.dp)) }
            val others = product.priceCandidates.distinct()
            if (others.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text("Other prices found - tap to use", fontSize = 12.sp, color = PP.TextLo)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    others.take(6).forEach { c ->
                        AssistChip(onClick = { priceText = priceToText(c) }, label = { Text(Money.format(c), color = PP.TextHi) })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            DropdownPicker("Category", category, repo.categoryNames, { c ->
                category = c; sub = repo.subcategoriesOf(c).firstOrNull() ?: CategoryRepository.UNCATEGORIZED
            })
            Spacer(Modifier.height(10.dp))
            DropdownPicker("Subcategory", sub, subs, { sub = it })
            Text(product.classEvidence, fontSize = 11.sp, color = PP.TextLo, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(12.dp))
            Text("How much do you need it?", fontSize = 12.sp, color = PP.TextLo)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.values().forEach { p ->
                    FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                }
            }
            if (line != null && line.reason.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(line.reason, fontSize = 12.sp, color = PP.TextHi)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRemove) { Text("Remove", color = PP.Error) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onSave(name, if (priceText.isBlank()) null else parsed, category, sub, priority) },
                    enabled = !priceError && name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun VerdictDetailsSheet(a: Assessment, products: List<Product>, onDismiss: () -> Unit) {
    val b = a.budget
    ModalBottomSheet(
        onDismissRequest = onDismiss, containerColor = PP.Surface, contentColor = PP.TextHi,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Why this verdict", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Section("Products")
            products.forEach { p ->
                val l = a.line(p.id) ?: return@forEach
                val tag = when (l.decision) {
                    Decision.BUY_NOW -> "✓ Buy now"
                    Decision.BUY_WITH_CAUTION -> "! Buy with caution"
                    Decision.WAIT -> "… Consider later" + (l.monthsToSave?.let { " (~$it mo)" } ?: "")
                    Decision.SKIP -> "✕ Skip for now"
                    Decision.NEEDS_PRICE -> "? Needs a price"
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(p.name, Modifier.weight(1f), maxLines = 1, fontSize = 14.sp)
                    Text(p.price?.let { Money.format(it) } ?: "-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(tag, fontSize = 12.sp, color = PP.Accent)
                if (l.reason.isNotBlank()) Text(l.reason, fontSize = 11.sp, color = PP.TextLo)
            }
            Spacer(Modifier.height(14.dp))
            Section("Your monthly cash flow")
            Line("Monthly income (I)", Money.format(b.income))
            if (b.usesHistory) Line("Average spending, last ${b.historyMonths} months (E)", Money.format(b.monthlySpend))
            else {
                Line("Fixed expenses + EMIs (E_f)", Money.format(b.fixedTotal))
                Line("Variable expenses (E_v)", Money.format(b.variable))
            }
            Line("Planned monthly savings (S_t)", Money.format(b.savingsTarget))
            Line("Left each month  D = I − E − S_t", Money.format(b.disposable), bold = true)
            Spacer(Modifier.height(10.dp))
            Section("Savings and safe budget")
            Line("Savings (S)", Money.format(b.savings))
            Line("Emergency reserve kept (S_e)", Money.format(b.reserve))
            Line("Savings above reserve  S_a = max(0, S − S_e)", Money.format(b.spendableSavings))
            Line("Monthly allowance  B_m = ${pct(b.alpha)} × max(0, D)", Money.format(b.monthlyAllowance))
            Line("Savings allowance  ${pct(b.beta)} × S_a", Money.format(b.savingsAllowance))
            Line("Safe purchase budget  B_safe", Money.format(b.safeBudget), bold = true)
            Spacer(Modifier.height(10.dp))
            Section("This purchase")
            Line("Total of priced products (P)", Money.format(a.totalAll))
            Line("Recommended to buy now", Money.format(a.totalBuyNow))
            Line("Safe budget left", Money.format(a.budgetLeft))
            Line("Savings after buying", Money.format(a.savingsAfter))
            Line("Spending this month after buying", Money.format(a.monthlySpendAfter))
            Line("Share of monthly income", "%.1f%%".format(a.incomeShareBuyNow * 100))
            Spacer(Modifier.height(14.dp))
            Text(
                (if (b.usesHistory) "Spending uses the average of your last ${b.historyMonths} complete months. "
                else "Spending uses the figures you entered - an estimate, not a forecast. ") +
                    "The ${pct(b.alpha)} and ${pct(b.beta)} shares are your own policy settings (Financial profile), not universal rules. " +
                    "With several products, must-haves are funded first, then cheaper items first, each only if it fits what is left. " +
                    "That greedy order is simple to follow but not guaranteed to be the best possible combination.",
                fontSize = 11.sp, color = PP.TextLo, lineHeight = 15.sp
            )
        }
    }
}

private fun pct(x: Double) = "${(x * 100).toInt()}%"

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), fontSize = 10.sp, letterSpacing = 1.2.sp, color = PP.TextLo, fontWeight = FontWeight.Medium)
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = PP.Divider)
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp), fontSize = 13.sp, color = if (bold) PP.TextHi else PP.TextLo)
        Text(value, fontSize = 13.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = PP.Surface, titleContentColor = PP.TextHi, textContentColor = PP.TextLo,
        title = { Text("How to scan a price") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "1. Hold the camera so the price tag sits inside the white corners. The price shows up in the dark box when it's readable.\n\n" +
                        "2. Tap the round button to capture. PingPaisa reads the price and name, then matches a category from your dataset.\n\n" +
                        "3. Use the photo button (bottom-left) to add one or many pictures or screenshots from your gallery. Tap + in the list to add more.\n\n" +
                        "4. Tap any product to fix its name, price, category or priority. Totals and the verdict update instantly.\n\n" +
                        "5. Shopping apps: use Share → PingPaisa on an Amazon or Flipkart product to send the link straight here.\n\n" +
                        "Tip: MRP and selling price are told apart when both are visible; check the price if the box says \"check this price\".",
                    fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = PP.Accent) } }
    )
}
