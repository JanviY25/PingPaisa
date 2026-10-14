package com.pingpaisa.app.data

import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.catalog.MatchSource
import com.pingpaisa.app.finance.Priority
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Where the price came from. Kept separate so a detected price is never silently replaced. */
enum class PriceSource(val label: String) {
    OCR("Read from the image"),
    PAGE("From the product page"),
    SHARED_TEXT("From the shared message"),
    MANUAL("Entered by you"),
    NONE("Not found")
}

enum class ItemState { PROCESSING, READY, NEEDS_PRICE, NEEDS_INFO }

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val imagePath: String? = null,
    val sourceUrl: String? = null,
    val contentHash: String? = null,
    val name: String = "",
    val nameIsGuess: Boolean = false,
    val brand: String? = null,
    val price: Double? = null,
    val priceSource: PriceSource = PriceSource.NONE,
    val priceConfidence: Float? = null,
    val priceCandidates: List<Double> = emptyList(),
    val priceNote: String? = null,
    val category: String = CategoryRepository.MISC,
    val subcategory: String = CategoryRepository.UNCATEGORIZED,
    val classSource: MatchSource = MatchSource.UNMATCHED,
    val classConfidence: Float = 0f,
    val classEvidence: String = "",
    val priority: Priority = Priority.NORMAL,
    val state: ItemState = ItemState.PROCESSING,
    val message: String? = null,
    val edited: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val lowConfidencePrice: Boolean
        get() = priceSource == PriceSource.OCR && (priceConfidence ?: 0f) < 0.6f
}

fun Product.toJson(): JSONObject = JSONObject().apply {
    put("id", id); put("imagePath", imagePath); put("sourceUrl", sourceUrl); put("contentHash", contentHash)
    put("name", name); put("nameIsGuess", nameIsGuess); put("brand", brand)
    if (price != null) put("price", price)
    put("priceSource", priceSource.name)
    if (priceConfidence != null) put("priceConfidence", priceConfidence.toDouble())
    put("priceCandidates", JSONArray(priceCandidates)); put("priceNote", priceNote)
    put("category", category); put("subcategory", subcategory)
    put("classSource", classSource.name); put("classConfidence", classConfidence.toDouble()); put("classEvidence", classEvidence)
    put("priority", priority.name); put("state", state.name); put("message", message)
    put("edited", JSONArray(edited.toList())); put("createdAt", createdAt)
}

private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

fun productFromJson(o: JSONObject): Product {
    val cands = o.optJSONArray("priceCandidates")
    val edited = o.optJSONArray("edited")
    val loadedState = enumOr(o.optString("state"), ItemState.NEEDS_INFO)
    val price = if (o.has("price") && !o.isNull("price")) o.optDouble("price") else null
    return Product(
        id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
        imagePath = o.str("imagePath"), sourceUrl = o.str("sourceUrl"), contentHash = o.str("contentHash"),
        name = o.optString("name"), nameIsGuess = o.optBoolean("nameIsGuess"), brand = o.str("brand"),
        price = price,
        priceSource = enumOr(o.optString("priceSource"), PriceSource.NONE),
        priceConfidence = if (o.has("priceConfidence")) o.optDouble("priceConfidence").toFloat() else null,
        priceCandidates = if (cands == null) emptyList() else (0 until cands.length()).map { cands.getDouble(it) },
        priceNote = o.str("priceNote"),
        category = o.optString("category").ifEmpty { CategoryRepository.MISC },
        subcategory = o.optString("subcategory").ifEmpty { CategoryRepository.UNCATEGORIZED },
        classSource = enumOr(o.optString("classSource"), MatchSource.UNMATCHED),
        classConfidence = o.optDouble("classConfidence", 0.0).toFloat(), classEvidence = o.optString("classEvidence"),
        priority = enumOr(o.optString("priority"), Priority.NORMAL),
        // a product that was still being analysed when the app died can't resume: ask for details instead
        state = if (loadedState == ItemState.PROCESSING) ItemState.NEEDS_INFO else loadedState,
        message = o.str("message") ?: if (loadedState == ItemState.PROCESSING) "Analysis was interrupted - check the details or remove it." else null,
        edited = if (edited == null) emptySet() else (0 until edited.length()).map { edited.getString(it) }.toSet(),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )
}
