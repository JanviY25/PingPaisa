package com.pingpaisa.app

import com.pingpaisa.app.recognition.Box
import com.pingpaisa.app.recognition.OcrLine
import com.pingpaisa.app.recognition.PriceExtractor
import com.pingpaisa.app.recognition.PriceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceExtractorTest {
    private fun lines(vararg t: String) = t.map { OcrLine(it) }
    private fun best(vararg t: String) = PriceExtractor.extract(lines(*t)).best?.value

    @Test fun rupeeFormats() {
        assertEquals(18999.0, best("₹18,999")!!, 0.0)
        assertEquals(18999.0, best("Rs. 18,999")!!, 0.0)
        assertEquals(18999.0, best("Rs.18,999")!!, 0.0)
        assertEquals(18999.0, best("INR 18999")!!, 0.0)
        assertEquals(1299.0, best("Rs. 1,299/-")!!, 0.0)
        assertEquals(123456.0, best("₹1,23,456")!!, 0.0)
        assertEquals(499.0, best("Price 499")!!, 0.0)
    }

    @Test fun ignoresNumbersThatAreNotPrices() {
        assertNull(best("8GB RAM 128GB storage"))
        assertNull(best("Call 9876543210"))
        assertNull(best("Pin 110001"))
        assertNull(best("12/05/2024"))
        assertNull(best("Rated 4.5 stars 2,345 ratings"))
        assertNull(best("5000 mAh battery"))
        assertNull(best("Flat 24% off"))
    }

    @Test fun ignoresEmiAndDiscountLines() {
        val r = PriceExtractor.extract(lines("₹18,999", "No Cost EMI ₹3,167/month", "You save ₹2,000"))
        assertEquals(18999.0, r.best!!.value, 0.0)
        assertEquals(1, r.candidates.size)
    }

    @Test fun sellingPriceBeatsMrp() {
        val r = PriceExtractor.extract(
            listOf(
                OcrLine("M.R.P.: ₹24,999", Box(0f, 100f, 300f, 120f)),
                OcrLine("₹18,999", Box(0f, 10f, 300f, 80f)),
                OcrLine("24% off", Box(0f, 130f, 100f, 150f))
            )
        )
        assertEquals(18999.0, r.best!!.value, 0.0)
        assertTrue(r.candidates.any { it.value == 24999.0 && it.kind == PriceKind.MRP })
    }

    @Test fun mrpAloneIsReportedWithNote() {
        val r = PriceExtractor.extract(lines("MRP ₹499 inclusive of all taxes"))
        assertEquals(499.0, r.best!!.value, 0.0)
        assertEquals(PriceKind.MRP, r.best!!.kind)
        assertNotNull(r.note)
    }

    @Test fun bareNumberIsLowConfidence() {
        val r = PriceExtractor.extract(lines("18999"))
        assertEquals(18999.0, r.best!!.value, 0.0)
        assertTrue(r.isLowConfidence)
    }

    @Test fun markedPriceIsConfident() {
        assertTrue(PriceExtractor.extract(lines("₹18,999")).confidence >= 0.8f)
    }

    @Test fun aimingBoxBreaksTies() {
        val inside = OcrLine("₹1,999", Box(100f, 100f, 200f, 140f))
        val outside = OcrLine("₹2,499", Box(400f, 400f, 500f, 440f))
        val r = PriceExtractor.extract(listOf(outside, inside), focus = Box(50f, 50f, 300f, 300f))
        assertEquals(1999.0, r.best!!.value, 0.0)
    }

    @Test fun biggerPrintWinsWithoutFocus() {
        val big = OcrLine("₹1,999", Box(0f, 0f, 200f, 80f))
        val small = OcrLine("₹2,499", Box(0f, 100f, 80f, 120f))
        assertEquals(1999.0, PriceExtractor.extract(listOf(small, big)).best!!.value, 0.0)
    }

    @Test fun emptyInput() {
        assertNull(PriceExtractor.extract(emptyList()).best)
    }
}
