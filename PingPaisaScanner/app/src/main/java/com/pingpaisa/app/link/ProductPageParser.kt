package com.pingpaisa.app.link

import com.pingpaisa.app.util.Money
import org.json.JSONArray
import org.json.JSONObject

data class PageInfo(
    val title: String? = null,
    val brand: String? = null,
    val imageUrl: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val priceSource: String? = null,   // "JSON-LD", "Open Graph", ...
    val blocked: Boolean = false
) {
    /** Non-INR prices are not auto-used: the UI asks the user for the rupee amount. */
    val priceUsable: Boolean get() = price != null && (currency == null || currency.equals("INR", true))
}

/**
 * Reads only what the page itself publishes for machines: schema.org JSON-LD Product/Offer, Open Graph and
 * product:* meta tags. Does not execute scripts or work around bot checks - a CAPTCHA page is reported as blocked.
 */
object ProductPageParser {
    private val LD = Regex("""<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val META = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z:\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
    private val TITLE = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BLOCK_MARKERS = listOf(
        "enter the characters you see below", "robot check", "to discuss automated access",
        "are you a human", "verify you are a human", "captcha", "access denied", "attention required"
    )

    fun parse(html: String): PageInfo {
        val lower = html.take(6000).lowercase()
        val metas = readMeta(html)
        var title: String? = null; var brand: String? = null; var image: String? = null
        var price: Double? = null; var currency: String? = null; var source: String? = null

        for (m in LD.findAll(html)) {
            val node = runCatching { parseJsonAny(m.groupValues[1].trim()) }.getOrNull() ?: continue
            val product = findProduct(node) ?: continue
            title = title ?: product.optString("name").ifBlank { null }
            brand = brand ?: brandOf(product)
            image = image ?: imageOf(product)
            if (price == null) {
                offerOf(product)?.let { (p, c) -> price = p; currency = c; source = "JSON-LD" }
            }
        }
        title = title ?: metas["og:title"] ?: metas["twitter:title"] ?: TITLE.find(html)?.groupValues?.get(1)
        image = image ?: metas["og:image"] ?: metas["twitter:image"]
        brand = brand ?: metas["product:brand"] ?: metas["og:brand"]
        if (price == null) {
            val raw = metas["product:price:amount"] ?: metas["og:price:amount"] ?: metas["itemprop:price"]
            val p = Money.parse(raw)
            if (p != null && p > 0) {
                price = p
                currency = metas["product:price:currency"] ?: metas["og:price:currency"] ?: metas["itemprop:pricecurrency"]
                source = "Open Graph"
            }
        }
        val cleanTitle = title?.let { cleanTitle(decode(it)) }?.takeIf { it.isNotBlank() }
        // A bot-check / CAPTCHA page is reported as blocked; we never try to get around it.
        val mentionsBlock = BLOCK_MARKERS.any { lower.contains(it) }
        val blocked = mentionsBlock && price == null && html.length < 30_000
        return PageInfo(
            title = if (blocked) null else cleanTitle, brand = brand?.let { decode(it) }?.takeIf { it.isNotBlank() },
            imageUrl = image?.takeIf { it.startsWith("http") }, price = price?.takeIf { it > 0 },
            currency = currency?.trim()?.takeIf { it.isNotEmpty() }, priceSource = source, blocked = blocked
        )
    }

    private fun readMeta(html: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (tag in META.findAll(html)) {
            val attrs = HashMap<String, String>()
            ATTR.findAll(tag.value).forEach { a ->
                attrs[a.groupValues[1].lowercase()] = a.groupValues[2].ifEmpty { a.groupValues[3] }
            }
            val content = attrs["content"] ?: continue
            val key = attrs["property"] ?: attrs["name"]
            if (key != null) out.putIfAbsent(key.lowercase(), content)
            attrs["itemprop"]?.let { out.putIfAbsent("itemprop:" + it.lowercase(), content) }
        }
        return out
    }

    private fun parseJsonAny(s: String): Any = if (s.trimStart().startsWith("[")) JSONArray(s) else JSONObject(s)

    private fun findProduct(node: Any?, depth: Int = 0): JSONObject? {
        if (depth > 6) return null
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) findProduct(node.opt(i), depth + 1)?.let { return it }
            is JSONObject -> {
                val t = node.opt("@type")
                val isProduct = when (t) {
                    is String -> t.equals("Product", true) || t.endsWith("Product", true)
                    is JSONArray -> (0 until t.length()).any { t.optString(it).contains("Product", true) }
                    else -> false
                }
                if (isProduct) return node
                findProduct(node.opt("@graph"), depth + 1)?.let { return it }
                findProduct(node.opt("mainEntity"), depth + 1)?.let { return it }
            }
        }
        return null
    }

    private fun brandOf(p: JSONObject): String? = when (val b = p.opt("brand")) {
        is String -> b
        is JSONObject -> b.optString("name")
        else -> null
    }?.ifBlank { null }

    private fun imageOf(p: JSONObject): String? = when (val i = p.opt("image")) {
        is String -> i
        is JSONArray -> if (i.length() > 0) (i.opt(0) as? String ?: (i.opt(0) as? JSONObject)?.optString("url")) else null
        is JSONObject -> i.optString("url")
        else -> null
    }?.ifBlank { null }

    private fun offerOf(p: JSONObject): Pair<Double, String?>? {
        val offers = p.opt("offers") ?: return null
        val list = when (offers) {
            is JSONArray -> (0 until offers.length()).mapNotNull { offers.optJSONObject(it) }
            is JSONObject -> listOf(offers)
            else -> emptyList()
        }
        for (o in list) {
            val raw = o.opt("price") ?: o.opt("lowPrice") ?: o.optJSONObject("priceSpecification")?.opt("price")
            val value = when (raw) {
                is Number -> raw.toDouble()
                is String -> Money.parse(raw)
                else -> null
            }
            if (value != null && value > 0) {
                val cur = o.optString("priceCurrency").ifBlank { o.optJSONObject("priceSpecification")?.optString("priceCurrency") }
                return value to cur?.ifBlank { null }
            }
        }
        return null
    }

    private fun decode(s: String): String = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    /** "Amazon.in: Buy boAt Rockerz 450 ... online at low price" -> "boAt Rockerz 450 ..." */
    fun cleanTitle(t: String): String = t
        .replace(Regex("(?i)^amazon\\.in\\s*:\\s*"), "")
        .replace(Regex("(?i)^buy\\s+"), "")
        .replace(Regex("(?i)\\s+(online\\s+)?(at|@)\\s+(best|low|lowest)\\s+price.*$"), "")
        .replace(Regex("(?i)\\s+online\\s+(at|in|on).*$"), "")
        .replace(Regex("(?i)\\s*[|\\-–:]\\s*(flipkart(\\.com)?|amazon(\\.in)?|myntra|ajio|nykaa|meesho)\\b.*$"), "")
        .trim()
}
