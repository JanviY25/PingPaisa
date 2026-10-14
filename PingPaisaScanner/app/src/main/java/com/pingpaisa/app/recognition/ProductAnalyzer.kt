package com.pingpaisa.app.recognition

import android.graphics.Bitmap
import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.catalog.Classification
import com.pingpaisa.app.catalog.ImageLabelHit
import com.pingpaisa.app.catalog.MatchSource
import com.pingpaisa.app.catalog.ProductClassifier
import com.pingpaisa.app.data.ImageStore
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.link.FetchResult
import com.pingpaisa.app.link.ProductPageFetcher
import com.pingpaisa.app.link.SharedContent
import com.pingpaisa.app.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnalysisResult(
    val name: String,
    val nameIsGuess: Boolean,
    val brand: String?,
    val price: Double?,
    val priceSource: PriceSource,
    val priceConfidence: Float?,
    val priceCandidates: List<Double>,
    val priceNote: String?,
    val classification: Classification,
    val message: String?,
    val imagePath: String? = null,
    val imageHash: String? = null
)

/**
 * The one pipeline behind camera, gallery, multi-image and shared-link inputs:
 *   image/link -> OCR + labels (+ optional AI) -> price (PriceExtractor) -> name -> category (ProductClassifier).
 * UI code never touches OCR or classification directly.
 */
class ProductAnalyzer(
    private val ocr: OcrEngine,
    private val repo: CategoryRepository,
    private val classifier: ProductClassifier,
    private val gemini: GeminiRecognizer?,
    private val fetcher: ProductPageFetcher,
    private val images: ImageStore
) {
    suspend fun analyzeImage(path: String, focus: FocusSpec?): AnalysisResult = withContext(Dispatchers.Default) {
        val bmp = ImageStore.decodeForOcr(path)
            ?: return@withContext failed("Couldn't open that image. Enter the details yourself.")
        var message: String? = null
        val lines = try { ocr.readText(bmp) } catch (e: Exception) {
            message = "Text recognition failed - enter the price yourself."; emptyList()
        }
        val focusBox = focus?.let { ViewportMapper.focusInImage(it, bmp.width, bmp.height) }
        val price = PriceExtractor.extract(lines, focusBox)
        val labels = try { ocr.labels(bmp) } catch (e: Exception) { emptyList() }
        val allText = lines.joinToString(" ") { it.text }

        var cls = classifier.classify(allText, labels)
        var name = ProductNamer.deriveName(lines)
        var guess = false
        var brand = findBrand(allText)

        if (gemini?.enabled == true && (name == null || cls.confidence < 0.8f)) {
            gemini.identify(bmp)?.let { ai ->
                if (ai.name != null) { name = ai.name; guess = false }
                brand = ai.brand ?: brand
                classifier.fromSuggestion(ai.category, ai.subcategory, "Suggested by AI from the photo, matched to your dataset")
                    ?.let { cls = it }
            }
        }
        if (name == null) {
            val top = labels.maxByOrNull { it.confidence }
            name = top?.text ?: "Unknown product"
            guess = true
        } else if (name!!.length < 4) guess = true

        if (price.best == null) {
            message = message ?: if (lines.isEmpty()) "No readable text found. Enter the price yourself." else "No price found. Enter it yourself."
        } else if (price.isLowConfidence) {
            message = "Price read with low confidence - please check it."
        }
        AnalysisResult(
            name = name!!, nameIsGuess = guess, brand = brand,
            price = price.best?.value, priceSource = if (price.best != null) PriceSource.OCR else PriceSource.NONE,
            priceConfidence = price.best?.let { price.confidence },
            priceCandidates = price.candidates.map { it.value }, priceNote = price.note,
            classification = cls, message = message
        )
    }

    suspend fun analyzeLink(content: SharedContent): AnalysisResult = withContext(Dispatchers.Default) {
        val url = content.url ?: return@withContext analyzeText(content)
        val textPrice = PriceExtractor.extract(content.text.lines().map { OcrLine(it) }).best
            ?.takeIf { it.hasCurrencyMarker }
        when (val r = fetcher.fetch(url)) {
            is FetchResult.Success -> {
                val info = r.info
                val image = r.imageBytes?.let { images.saveBytes(it) }
                val title = info.title ?: content.text.takeIf { it.isNotBlank() }
                val brand = info.brand ?: findBrand(listOfNotNull(title, content.text).joinToString(" "))
                var note: String? = null
                var message: String? = null
                val price: Double?
                val source: PriceSource
                when {
                    info.priceUsable -> {
                        price = info.price; source = PriceSource.PAGE
                        if (textPrice != null && textPrice.value != info.price) {
                            note = "The shared message said ${Money.format(textPrice.value)}; the page lists ${Money.format(info.price!!)}."
                        }
                    }
                    textPrice != null -> { price = textPrice.value; source = PriceSource.SHARED_TEXT }
                    else -> {
                        price = null; source = PriceSource.NONE
                        message = if (info.price != null) "The page lists the price in ${info.currency}. Enter it in rupees."
                        else "The page didn't publish a price. Enter it yourself."
                    }
                }
                val labels: List<ImageLabelHit> = if (image != null) {
                    ImageStore.decodeForOcr(image.path)?.let { runCatching { ocr.labels(it) }.getOrDefault(emptyList()) } ?: emptyList()
                } else emptyList()
                val text = listOfNotNull(title, brand, content.text).joinToString(" ")
                val cls = classifier.classify(text, labels, content.host)
                AnalysisResult(
                    name = title ?: "Product from ${content.host ?: "link"}", nameIsGuess = title == null, brand = brand,
                    price = price, priceSource = source, priceConfidence = null,
                    priceCandidates = listOfNotNull(info.price, textPrice?.value).distinct(), priceNote = note,
                    classification = cls, message = message, imagePath = image?.path, imageHash = image?.hash
                )
            }
            is FetchResult.Failure -> {
                val cls = classifier.classify(content.text, emptyList(), content.host)
                AnalysisResult(
                    name = content.text.ifBlank { "Product from ${content.host ?: "link"}" }.take(80),
                    nameIsGuess = true, brand = findBrand(content.text),
                    price = textPrice?.value, priceSource = if (textPrice != null) PriceSource.SHARED_TEXT else PriceSource.NONE,
                    priceConfidence = null, priceCandidates = listOfNotNull(textPrice?.value), priceNote = null,
                    classification = cls, message = r.message + " Enter the details yourself."
                )
            }
        }
    }

    /** Shared plain text without a link ("iPhone 15 Rs 59,999"). */
    fun analyzeText(content: SharedContent): AnalysisResult {
        val price = PriceExtractor.extract(content.text.lines().map { OcrLine(it) })
        val cls = classifier.classify(content.text)
        return AnalysisResult(
            name = content.text.take(80), nameIsGuess = true, brand = findBrand(content.text),
            price = price.best?.value, priceSource = if (price.best != null) PriceSource.SHARED_TEXT else PriceSource.NONE,
            priceConfidence = null, priceCandidates = price.candidates.map { it.value }, priceNote = price.note,
            classification = cls, message = if (price.best == null) "No price in the shared text. Enter it yourself." else null
        )
    }

    private fun failed(msg: String) = AnalysisResult(
        "Unknown product", true, null, null, PriceSource.NONE, null, emptyList(), null, classifier.unmatched(), msg
    )

    private fun findBrand(text: String): String? {
        val t = ProductClassifier.normalize(text)
        return repo.brands.sortedByDescending { it.length }.firstOrNull { b ->
            Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(ProductClassifier.normalize(b)) + "(?![\\p{L}\\p{N}])").containsMatchIn(t)
        }
    }
}
