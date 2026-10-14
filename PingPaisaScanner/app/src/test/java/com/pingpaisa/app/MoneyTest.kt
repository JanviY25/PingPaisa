package com.pingpaisa.app

import com.pingpaisa.app.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun formatsIndianGrouping() {
        assertEquals("₹18,999", Money.format(18999.0))
        assertEquals("₹12,34,567", Money.format(1234567.0))
        assertEquals("₹12,34,567.50", Money.format(1234567.5))
        assertEquals("₹999", Money.format(999.0))
        assertEquals("₹0", Money.format(0.0))
        assertEquals("-₹1,500", Money.format(-1500.0))
        assertEquals("1,00,000", Money.groupIndian(100000))
    }

    @Test fun parsesCommonRupeeSpellings() {
        assertEquals(18999.0, Money.parse("₹ 18,999")!!, 0.0)
        assertEquals(18999.0, Money.parse("Rs. 18,999/-")!!, 0.0)
        assertEquals(18999.5, Money.parse("INR 18999.50")!!, 0.0)
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse(null))
    }
}
