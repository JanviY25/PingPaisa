package com.pingpaisa.app.catalog

enum class MatchSource { KEYWORD, IMAGE_LABEL, MERCHANT, AI, MANUAL, UNMATCHED }

data class ImageLabelHit(val text: String, val confidence: Float)

data class Classification(
    val category: String,
    val subcategory: String,
    val confidence: Float,
    val source: MatchSource,
    val evidence: String
)

/**
 * One classifier for camera photos, gallery images, shared links and edited records.
 * Order: product-type keywords in text -> image labels -> merchant aliases from the dataset ->
 * Miscellaneous / Uncategorized. Anything not matched is Miscellaneous by design.
 */
class ProductClassifier(private val repo: CategoryRepository) {

    private class Compiled(val rule: KeywordRule, val regex: Regex?)
    private class Hit(val rule: KeywordRule, val distinct: Int, val sample: String, val maxWords: Int) {
        val score: Double get() = rule.weight * (distinct + 0.25 * (maxWords - 1))
    }

    private val compiled: List<Compiled> = repo.rules.map { r ->
        val kws = r.keywords.map { normalize(it) }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
        val regex = if (kws.isEmpty()) null else Regex(
            "(?<![\\p{L}\\p{N}])(?:" + kws.joinToString("|") { Regex.escape(it) } + ")(?:s|es)?(?![\\p{L}\\p{N}])"
        )
        Compiled(r, regex)
    }

    private class MerchantKey(val m: Merchant, val alias: String, val regex: Regex)

    private val merchantKeys: List<MerchantKey> = repo.merchants.flatMap { m ->
        (m.aliases + m.name).map { normalize(it) }.filter { it.length >= 4 }.distinct().map { a ->
            MerchantKey(m, a, Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(a) + "(?![\\p{L}\\p{N}])"))
        }
    }.sortedByDescending { it.alias.length }

    fun classify(text: String?, labels: List<ImageLabelHit> = emptyList(), hostHint: String? = null): Classification {
        val t = normalize(text.orEmpty())
        if (t.isNotEmpty()) {
            scan(t)?.let { return result(it, MatchSource.KEYWORD, "Matched “${it.sample}” in the product text", 1.0f) }
        }
        var bestLabel: Pair<Hit, Float>? = null
        for (l in labels.filter { it.confidence >= 0.5f }) {
            val hit = scan(normalize(l.text)) ?: continue
            val weighted = hit.score * l.confidence
            if (bestLabel == null || weighted > bestLabel.first.score * bestLabel.second) bestLabel = hit to l.confidence
        }
        bestLabel?.let { (hit, c) ->
            return result(hit, MatchSource.IMAGE_LABEL, "Photo looks like “${hit.sample}” (${(c * 100).toInt()}% sure)", c * 0.85f)
        }
        val merchantText = normalize(listOfNotNull(text, hostHint).joinToString(" "))
        merchantKeys.firstOrNull { it.regex.containsMatchIn(merchantText) }?.let { k ->
            val conf = if (k.alias.length >= 6) 0.65f else 0.55f
            return Classification(k.m.category, k.m.subcategory, conf, MatchSource.MERCHANT, "Matched merchant “${k.m.name}” in your dataset")
        }
        return unmatched()
    }

    /** Validate an externally suggested (e.g. AI) pair against the dataset before trusting it. */
    fun fromSuggestion(category: String?, subcategory: String?, evidence: String, confidence: Float = 0.7f): Classification? {
        val pair = repo.canonical(category, subcategory) ?: return null
        if (pair.first == CategoryRepository.MISC && pair.second == CategoryRepository.UNCATEGORIZED) return null
        return Classification(pair.first, pair.second, confidence, MatchSource.AI, evidence)
    }

    fun manual(category: String, subcategory: String) =
        Classification(category, subcategory, 1f, MatchSource.MANUAL, "Chosen by you")

    fun unmatched() = Classification(
        CategoryRepository.MISC, CategoryRepository.UNCATEGORIZED, 0f, MatchSource.UNMATCHED,
        "No product type or merchant matched - filed under Miscellaneous. Tap to choose a category."
    )

    private fun scan(text: String): Hit? {
        var best: Hit? = null
        for (c in compiled) {
            val rx = c.regex ?: continue
            val found = rx.findAll(text).map { it.value }.toList()
            if (found.isEmpty()) continue
            val distinct = found.distinct()
            val longest = distinct.maxByOrNull { it.length } ?: distinct.first()
            val words = distinct.maxOf { it.trim().split(' ').size }
            val hit = Hit(c.rule, distinct.size, longest, words)
            if (best == null || hit.score > best.score) best = hit
        }
        return best
    }

    private fun result(hit: Hit, source: MatchSource, evidence: String, scale: Float): Classification {
        var conf = 0.55 + 0.15 * (hit.distinct - 1) + 0.05 * (hit.maxWords - 1)
        if (hit.rule.weight < 1.0) conf *= 0.85
        return Classification(hit.rule.category, hit.rule.subcategory, (minOf(0.95, conf) * scale).toFloat(), source, evidence)
    }

    companion object {
        /** lower-case, punctuation -> space, collapsed. "T-Shirt," -> "t shirt". */
        fun normalize(s: String): String =
            s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    }
}
