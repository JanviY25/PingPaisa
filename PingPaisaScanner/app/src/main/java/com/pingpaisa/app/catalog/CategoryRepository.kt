package com.pingpaisa.app.catalog

import org.json.JSONArray
import org.json.JSONObject

data class CategoryNode(val name: String, val subcategories: List<String>)

/** One entry of the dataset's "merchants" array (name, aliases, category, subcategory, type). */
data class Merchant(
    val name: String,
    val aliases: List<String>,
    val category: String,
    val subcategory: String
)

/** Product-type keywords from product_keywords.json, tied to a dataset (category, subcategory). */
data class KeywordRule(val category: String, val subcategory: String, val weight: Double, val keywords: List<String>)

/**
 * Single source of truth for categories. Loaded from india_expense_categorization_dataset.json
 * (schema: { categories:[{category, subcategories[]}], merchants:[{name, aliases[], category,
 * subcategory, type}], meta }). The dataset has NO price fields, so prices always come from OCR,
 * the product page or the user - never from the dataset.
 */
class CategoryRepository private constructor(
    val categories: List<CategoryNode>,
    val merchants: List<Merchant>,
    val rules: List<KeywordRule>,
    val brands: List<String>,
    val droppedRules: List<String>,
    val loadError: String?
) {
    val categoryNames: List<String> get() = categories.map { it.name }

    fun subcategoriesOf(category: String): List<String> =
        categories.firstOrNull { it.name == category }?.subcategories ?: emptyList()

    fun isValid(category: String, subcategory: String): Boolean = subcategoriesOf(category).contains(subcategory)

    /** Case-insensitive repair of an externally supplied pair (e.g. from an AI model). */
    fun canonical(category: String?, subcategory: String?): Pair<String, String>? {
        if (category.isNullOrBlank() || subcategory.isNullOrBlank()) return null
        val cat = categories.firstOrNull { it.name.equals(category.trim(), ignoreCase = true) } ?: return null
        val sub = cat.subcategories.firstOrNull { it.equals(subcategory.trim(), ignoreCase = true) } ?: return null
        return cat.name to sub
    }

    /** Compact taxonomy text used in the optional AI prompt. */
    fun taxonomyPrompt(): String =
        categories.joinToString("\n") { "${it.name}: ${it.subcategories.joinToString(" | ")}" }

    companion object {
        const val MISC = "Miscellaneous"
        const val UNCATEGORIZED = "Uncategorized"

        /** Never throws: malformed/missing JSON yields a Miscellaneous-only repository plus [loadError]. */
        fun parse(datasetJson: String?, keywordsJson: String?): CategoryRepository {
            var error: String? = null
            val cats = ArrayList<CategoryNode>()
            val merchants = ArrayList<Merchant>()
            try {
                val root = JSONObject(datasetJson ?: throw IllegalStateException("dataset missing"))
                val ca = root.getJSONArray("categories")
                for (i in 0 until ca.length()) {
                    val o = ca.getJSONObject(i)
                    cats += CategoryNode(o.getString("category"), strings(o.optJSONArray("subcategories")))
                }
                val ma = root.optJSONArray("merchants") ?: JSONArray()
                for (i in 0 until ma.length()) {
                    val o = ma.getJSONObject(i)
                    merchants += Merchant(
                        o.getString("name"), strings(o.optJSONArray("aliases")),
                        o.getString("category"), o.getString("subcategory")
                    )
                }
            } catch (e: Exception) {
                error = "Category dataset could not be read (${e.javaClass.simpleName}); using Miscellaneous only."
                cats.clear(); merchants.clear()
            }
            if (cats.none { it.name == MISC }) cats += CategoryNode(MISC, listOf(UNCATEGORIZED))
            else if (cats.first { it.name == MISC }.subcategories.none { it == UNCATEGORIZED }) {
                val i = cats.indexOfFirst { it.name == MISC }
                cats[i] = cats[i].copy(subcategories = cats[i].subcategories + UNCATEGORIZED)
            }

            val rules = ArrayList<KeywordRule>()
            val dropped = ArrayList<String>()
            val brands = ArrayList<String>()
            try {
                if (keywordsJson != null) {
                    val root = JSONObject(keywordsJson)
                    val ra = root.optJSONArray("rules") ?: JSONArray()
                    for (i in 0 until ra.length()) {
                        val o = ra.getJSONObject(i)
                        val c = o.getString("category")
                        val s = o.getString("subcategory")
                        val valid = cats.firstOrNull { it.name == c }?.subcategories?.contains(s) == true
                        if (valid) rules += KeywordRule(c, s, o.optDouble("weight", 1.0), strings(o.optJSONArray("keywords")))
                        else dropped += "$c / $s"
                    }
                    brands += strings(root.optJSONArray("brands"))
                }
            } catch (e: Exception) {
                error = (error?.plus(" ") ?: "") + "Keyword rules could not be read (${e.javaClass.simpleName})."
            }
            return CategoryRepository(cats, merchants, rules, brands, dropped, error)
        }

        private fun strings(a: JSONArray?): List<String> {
            if (a == null) return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
    }
}
