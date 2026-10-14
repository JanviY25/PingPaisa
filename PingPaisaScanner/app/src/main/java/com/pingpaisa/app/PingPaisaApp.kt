package com.pingpaisa.app

import android.app.Application
import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.catalog.ProductClassifier
import com.pingpaisa.app.data.ImageStore
import com.pingpaisa.app.data.ProductStore
import com.pingpaisa.app.finance.ExpenseHistoryProvider
import com.pingpaisa.app.finance.FinancialProfileStore
import com.pingpaisa.app.finance.NoExpenseHistory
import com.pingpaisa.app.link.ProductPageFetcher
import com.pingpaisa.app.recognition.GeminiRecognizer
import com.pingpaisa.app.recognition.OcrEngine
import com.pingpaisa.app.recognition.ProductAnalyzer

/** Tiny service locator - swap pieces here when merging into the main PingPaisa app (e.g. real expense history). */
class PingPaisaApp : Application() {
    val categories: CategoryRepository by lazy {
        fun asset(name: String) = try { assets.open(name).bufferedReader().use { it.readText() } } catch (e: Exception) { null }
        CategoryRepository.parse(asset("india_expense_categorization_dataset.json"), asset("product_keywords.json"))
    }
    val classifier by lazy { ProductClassifier(categories) }
    val ocr by lazy { OcrEngine() }
    val imageStore by lazy { ImageStore(this) }
    val productStore by lazy { ProductStore(this) }
    val profileStore by lazy { FinancialProfileStore(this) }
    val expenseHistory: ExpenseHistoryProvider = NoExpenseHistory
    private val gemini by lazy { GeminiRecognizer(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL, categories.taxonomyPrompt()) }
    val analyzer by lazy {
        ProductAnalyzer(ocr, categories, classifier, gemini.takeIf { it.enabled }, ProductPageFetcher(this), imageStore)
    }
}
