package com.tgfinance.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {

    @Test
    fun `parses german and english notation`() {
        assertEquals(1250L, AmountParser.parse("12,50"))
        assertEquals(1250L, AmountParser.parse("12.50"))
        assertEquals(123456L, AmountParser.parse("1.234,56"))
        assertEquals(123456L, AmountParser.parse("1,234.56"))
        assertEquals(123400L, AmountParser.parse("1.234"))
        assertEquals(100000000L, AmountParser.parse("1.000.000"))
        assertEquals(500L, AmountParser.parse("5"))
        assertEquals(50L, AmountParser.parse(",5"))
        assertEquals(1999L, AmountParser.parse(" 19,99 € "))
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(AmountParser.parse(""))
        assertNull(AmountParser.parse("abc"))
        assertNull(AmountParser.parse("1,234")) // a single comma is a decimal comma: 3 decimals
        assertNull(AmountParser.parse("1,999"))
        assertNull(AmountParser.parse("12,345,6"))
        assertNull(AmountParser.parse("-5"))
        assertNull(AmountParser.parse("."))
    }

    @Test
    fun `formats german euro`() {
        val formatter = MoneyFormatter("EUR", Locale.GERMANY)
        assertEquals("1.234,56 €", formatter.format(123456))
        assertEquals("-5,00 €", formatter.format(-500))
        assertEquals("+5,00 €", formatter.format(500, showPlusSign = true))
        assertEquals("12,50", formatter.formatPlain(1250))
    }

    @Test
    fun `compact format`() {
        val formatter = MoneyFormatter("EUR", Locale.GERMANY)
        assertEquals("12k €", formatter.formatCompact(1_234_500))
        assertEquals("999 €", formatter.formatCompact(99_900))
    }
}
