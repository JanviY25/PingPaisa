package com.pingpaisa.app

import com.pingpaisa.app.link.ProductPageParser
import com.pingpaisa.app.link.SharedLinkParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkTests {
    @Test fun extractsUrlAndLeadText() {
        val c = SharedLinkParser.parse("Check out this Samsung Galaxy M14 at ₹12,999 on Flipkart https://fkrt.cc/abc123")!!
        assertEquals("https://fkrt.cc/abc123", c.url)
        assertEquals("Samsung Galaxy M14 at ₹12,999 on Flipkart", c.text)
    }

    @Test fun trimsTrailingPunctuation() {
        assertEquals("https://www.amazon.in/dp/B0XYZ?tag=1", SharedLinkParser.parse("See (https://www.amazon.in/dp/B0XYZ?tag=1).")!!.url)
    }

    @Test fun plainTextAndBlank() {
        val c = SharedLinkParser.parse("iPhone 15 Rs 59,999")!!
        assertNull(c.url)
        assertEquals("iPhone 15 Rs 59,999", c.text)
        assertNull(SharedLinkParser.parse("   "))
        assertNull(SharedLinkParser.parse(null))
    }

    @Test fun hostAndDuplicateKey() {
        assertEquals("amazon.in", SharedLinkParser.parse("https://www.amazon.in/dp/X")!!.host)
        assertEquals(SharedLinkParser.normalizedKey("https://www.Amazon.in/dp/X/#r"), SharedLinkParser.normalizedKey("http://amazon.in/dp/x"))
    }

    @Test fun readsJsonLdProduct() {
        val html = """<html><head><title>Amazon.in: Buy boAt Airdopes online at low price</title>
            <script type="application/ld+json">{"@context":"https://schema.org","@type":"Product","name":"boAt Airdopes 141",
            "brand":{"@type":"Brand","name":"boAt"},"image":["https://img.example/x.jpg"],
            "offers":{"@type":"Offer","priceCurrency":"INR","price":"1299.00"}}</script></head><body></body></html>"""
        val p = ProductPageParser.parse(html)
        assertEquals("boAt Airdopes 141", p.title)
        assertEquals("boAt", p.brand)
        assertEquals(1299.0, p.price!!, 0.0)
        assertTrue(p.priceUsable)
        assertEquals("https://img.example/x.jpg", p.imageUrl)
        assertFalse(p.blocked)
    }

    @Test fun readsGraphWithOfferArray() {
        val html = """<script type="application/ld+json">{"@graph":[{"@type":"WebSite"},
            {"@type":"Product","name":"Desk Lamp","offers":[{"price":999,"priceCurrency":"INR"}]}]}</script>"""
        val p = ProductPageParser.parse(html)
        assertEquals("Desk Lamp", p.title)
        assertEquals(999.0, p.price!!, 0.0)
    }

    @Test fun readsOpenGraphAndCleansTitle() {
        val html = """<head><meta property="og:title" content="Nike Shoes &amp; Co | Myntra">
            <meta property="product:price:amount" content="4,999"><meta property="product:price:currency" content="INR"></head>"""
        val p = ProductPageParser.parse(html)
        assertEquals("Nike Shoes & Co", p.title)
        assertEquals(4999.0, p.price!!, 0.0)
        assertEquals("Open Graph", p.priceSource)
    }

    @Test fun foreignCurrencyIsNotAutoUsed() {
        val html = """<meta property="og:title" content="Gadget"><meta property="og:price:amount" content="99.00">
            <meta property="og:price:currency" content="USD">"""
        val p = ProductPageParser.parse(html)
        assertEquals(99.0, p.price!!, 0.0)
        assertFalse(p.priceUsable)
    }

    @Test fun botCheckPagesAreReportedAsBlocked() {
        val p = ProductPageParser.parse("<html><title>Robot Check</title><body>Enter the characters you see below</body></html>")
        assertTrue(p.blocked)
        assertNull(p.title)
    }
}
