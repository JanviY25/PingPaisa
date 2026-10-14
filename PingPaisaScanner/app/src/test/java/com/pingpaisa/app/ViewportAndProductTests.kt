package com.pingpaisa.app

import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.PriceSource
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.data.productFromJson
import com.pingpaisa.app.data.toJson
import com.pingpaisa.app.finance.Priority
import com.pingpaisa.app.recognition.Box
import com.pingpaisa.app.recognition.FocusSpec
import com.pingpaisa.app.recognition.ProductNamer
import com.pingpaisa.app.recognition.OcrLine
import com.pingpaisa.app.recognition.ViewportMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ViewportAndProductTests {
    @Test fun mapsBracketsIntoCameraPixels() {
        // view 1000x2000 shows a 3000x4000 image cover-scaled by 0.5, cropping 500px on each side
        val box = ViewportMapper.focusInImage(FocusSpec(1000, 2000, Box(100f, 200f, 900f, 1000f)), 3000, 4000)!!
        assertEquals(700f, box.left, 0.01f)
        assertEquals(400f, box.top, 0.01f)
        assertEquals(2300f, box.right, 0.01f)
        assertEquals(2000f, box.bottom, 0.01f)
        assertNull(ViewportMapper.focusInImage(FocusSpec(0, 0, Box(0f, 0f, 1f, 1f)), 10, 10))
    }

    @Test fun productJsonRoundTrip() {
        val p = Product(
            name = "Shoes", price = 3999.0, priceSource = PriceSource.OCR, priceConfidence = 0.9f,
            priceCandidates = listOf(3999.0, 5999.0), priority = Priority.MUST_HAVE, state = ItemState.READY,
            edited = setOf("price")
        )
        val back = productFromJson(org.json.JSONObject(p.toJson().toString()))
        assertEquals(p.name, back.name)
        assertEquals(3999.0, back.price!!, 0.0)
        assertEquals(listOf(3999.0, 5999.0), back.priceCandidates)
        assertEquals(Priority.MUST_HAVE, back.priority)
        assertEquals(setOf("price"), back.edited)
        assertNull(productFromJson(org.json.JSONObject(Product(name = "x").toJson().toString())).price)
    }

    @Test fun interruptedAnalysisBecomesNeedsInfo() {
        val back = productFromJson(org.json.JSONObject(Product(name = "x", state = ItemState.PROCESSING).toJson().toString()))
        assertEquals(ItemState.NEEDS_INFO, back.state)
        assertNotNull(back.message)
    }

    @Test fun namerPicksLargestNonPriceText() {
        val lines = listOf(
            OcrLine("boAt Rockerz 450", Box(0f, 10f, 300f, 60f)),
            OcrLine("Bluetooth Headphones", Box(0f, 70f, 300f, 110f)),
            OcrLine("MRP ₹3,990 incl. taxes", Box(0f, 120f, 200f, 135f)),
            OcrLine("Net Qty 1 N", Box(0f, 140f, 100f, 150f))
        )
        assertEquals("boAt Rockerz 450 Bluetooth Headphones", ProductNamer.deriveName(lines))
        assertNull(ProductNamer.deriveName(listOf(OcrLine("₹499"))))
    }
}
