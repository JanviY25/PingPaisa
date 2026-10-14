package com.pingpaisa.app

import com.pingpaisa.app.catalog.CategoryRepository
import com.pingpaisa.app.catalog.ImageLabelHit
import com.pingpaisa.app.catalog.MatchSource
import com.pingpaisa.app.catalog.ProductClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductClassifierTest {
    private val assets = File("src/main/assets")
    private val repo = CategoryRepository.parse(
        File(assets, "india_expense_categorization_dataset.json").readText(),
        File(assets, "product_keywords.json").readText()
    )
    private val c = ProductClassifier(repo)

    private fun check(text: String, cat: String, sub: String) {
        val r = c.classify(text)
        assertEquals("$text -> category", cat, r.category)
        assertEquals("$text -> sub", sub, r.subcategory)
    }

    @Test fun datasetLoadsCompletely() {
        assertNull(repo.loadError)
        assertEquals(18, repo.categories.size)
        assertEquals(485, repo.merchants.size)
        assertTrue("dropped rules: ${repo.droppedRules}", repo.droppedRules.isEmpty())
        assertTrue(repo.isValid("Shopping", "Footwear"))
    }

    @Test fun everyKeywordRuleUsesARealDatasetPair() {
        repo.rules.forEach { assertTrue("${it.category}/${it.subcategory}", repo.isValid(it.category, it.subcategory)) }
    }

    @Test fun productTypes() {
        check("Nike Air Zoom Running Shoes for Men", "Shopping", "Footwear")
        check("boAt Rockerz 450 Bluetooth Headphones", "Shopping", "Electronics/Gadgets")
        check("Allen Solly Men Slim Fit Cotton Shirt", "Shopping", "Clothing/Fashion")
        check("Samsung 55 inch 4K Smart TV", "Shopping", "Electronics/Gadgets")
        check("Classmate pen set", "Stationary/Office Supplies", "Office Supplies")
        check("Kindle Unlimited subscription", "Digital Subscriptions & Software", "News/Reading/Audiobooks")
        check("Chocolate ice cream tub", "Food & Dining", "Bakery/Sweets")
    }

    @Test fun unknownProductsAreMiscellaneous() {
        val r = c.classify("Xyz widget thingamajig")
        assertEquals("Miscellaneous", r.category)
        assertEquals("Uncategorized", r.subcategory)
        assertEquals(MatchSource.UNMATCHED, r.source)
    }

    @Test fun imageLabelsAreUsedWhenTextIsSilent() {
        val r = c.classify("", listOf(ImageLabelHit("Shoe", 0.9f)))
        assertEquals("Footwear", r.subcategory)
        assertEquals(MatchSource.IMAGE_LABEL, r.source)
    }

    @Test fun merchantAliasesFromTheDatasetAreTheFallback() {
        val r = c.classify("Order from Myntra")
        assertEquals(MatchSource.MERCHANT, r.source)
        assertEquals("Clothing/Fashion", r.subcategory)
        val host = c.classify("", hostHint = "www.flipkart.com")
        assertEquals("Online Marketplace", host.subcategory)
        assertEquals(MatchSource.UNMATCHED, c.classify("MCD").source) // 3-letter aliases are ignored in free text
    }

    @Test fun aiSuggestionsMustExistInTheDataset() {
        assertNotNull(c.fromSuggestion("shopping", "footwear", "x"))
        assertNull(c.fromSuggestion("Shopping", "Space Rockets", "x"))
        assertNull(c.fromSuggestion("Miscellaneous", "Uncategorized", "x"))
    }

    @Test fun malformedDatasetFallsBackToMiscellaneous() {
        val bad = CategoryRepository.parse("{ nope", null)
        assertNotNull(bad.loadError)
        assertTrue(bad.isValid("Miscellaneous", "Uncategorized"))
        assertEquals("Miscellaneous", ProductClassifier(bad).classify("shoes").category)
    }
}
