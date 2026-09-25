package com.example.smart_expense_tracker

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.math.min

data class CategoryResult(val category: String, val subcategory: String)

object Categorizer {

    // normalized alias -> CategoryResult
    private var aliasMap: Map<String, CategoryResult> = emptyMap()
    private var initialized = false

    // Strip everything except letters/numbers, uppercase — so "Domino's", "DOMINO S", "dominos"
    // all normalize to the same string: "DOMINOS"
    private fun normalize(text: String): String {
        return text.uppercase().filter { it.isLetterOrDigit() }
    }

    fun init(context: Context) {
        if (initialized) return

        val jsonText = context.assets.open("india_expense_categorization_dataset.json")
            .bufferedReader()
            .use { it.readText() }

        val root = JSONObject(jsonText)
        val merchants = root.getJSONArray("merchants")

        val map = mutableMapOf<String, CategoryResult>()
        for (i in 0 until merchants.length()) {
            val merchant = merchants.getJSONObject(i)
            val category = merchant.getString("category")
            val subcategory = if (merchant.has("subcategory")) merchant.getString("subcategory") else "Miscellaneous"
            val result = CategoryResult(category, subcategory)

            val aliases = merchant.getJSONArray("aliases")
            for (j in 0 until aliases.length()) {
                val normalizedAlias = normalize(aliases.getString(j))
                if (normalizedAlias.isNotBlank()) {
                    map[normalizedAlias] = result
                }
            }
        }

        aliasMap = map
        initialized = true
        Log.d("Categorizer", "Loaded ${aliasMap.size} normalized merchant aliases")
    }

    fun getAllCategories(): List<String> {
        return (aliasMap.values.map { it.category }.toSet() + setOf("Uncategorized")).sorted()
    }

    // Simple Levenshtein edit distance between two strings
    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + min(dp[i - 1][j - 1], min(dp[i - 1][j], dp[i][j - 1]))
                }
            }
        }
        return dp[a.length][b.length]
    }

    fun categorize(recipient: String): CategoryResult {
        val normalizedRecipient = normalize(recipient)
        if (normalizedRecipient.isBlank()) return CategoryResult("Uncategorized", "Miscellaneous")

        // Pass 1: exact substring match on normalized text (fast, handles most real cases,
        // including "Domino's" vs "DOMINOS" since punctuation is stripped)
        for ((alias, result) in aliasMap) {
            if (normalizedRecipient.contains(alias)) {
                return result
            }
        }

        // Pass 2: fuzzy fallback — only for aliases of similar length to avoid nonsense matches,
        // and only accept if the edit distance is small relative to the alias length
        var bestMatch: CategoryResult? = null
        var bestDistance = Int.MAX_VALUE

        for ((alias, result) in aliasMap) {
            if (alias.length < 4) continue // skip very short aliases, too risky for fuzzy matching
            val lengthDiff = kotlin.math.abs(alias.length - normalizedRecipient.length)
            if (lengthDiff > 4) continue // skip aliases whose length is wildly different

            val distance = editDistance(alias, normalizedRecipient.take(alias.length + 4))
            val threshold = (alias.length * 0.2).toInt().coerceAtLeast(1) // allow ~20% character difference

            if (distance <= threshold && distance < bestDistance) {
                bestDistance = distance
                bestMatch = result
            }
        }

        return bestMatch ?: CategoryResult("Uncategorized", "Miscellaneous")
    }
}