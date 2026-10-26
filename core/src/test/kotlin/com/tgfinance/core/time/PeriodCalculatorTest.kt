package com.tgfinance.core.time

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PeriodCalculatorTest {

    @Test
    fun `calendar month when month starts on the first`() {
        val range = PeriodCalculator.month(LocalDate.of(2024, 2, 15))
        assertEquals(LocalDate.of(2024, 2, 1), range.start)
        assertEquals(LocalDate.of(2024, 2, 29), range.endInclusive)
    }

    @Test
    fun `custom month start before start day belongs to previous period`() {
        val range = PeriodCalculator.month(LocalDate.of(2024, 3, 10), monthStartDay = 25)
        assertEquals(LocalDate.of(2024, 2, 25), range.start)
        assertEquals(LocalDate.of(2024, 3, 24), range.endInclusive)
    }

    @Test
    fun `custom month start on start day begins new period`() {
        val range = PeriodCalculator.month(LocalDate.of(2024, 3, 25), monthStartDay = 25)
        assertEquals(LocalDate.of(2024, 3, 25), range.start)
        assertEquals(LocalDate.of(2024, 4, 24), range.endInclusive)
    }

    @Test
    fun `month start day is clamped to 28`() {
        val range = PeriodCalculator.month(LocalDate.of(2024, 3, 30), monthStartDay = 31)
        assertEquals(LocalDate.of(2024, 3, 28), range.start)
    }

    @Test
    fun `week respects first day of week`() {
        val wednesday = LocalDate.of(2024, 5, 8)
        assertEquals(LocalDate.of(2024, 5, 6), PeriodCalculator.week(wednesday, DayOfWeek.MONDAY).start)
        assertEquals(LocalDate.of(2024, 5, 5), PeriodCalculator.week(wednesday, DayOfWeek.SUNDAY).start)
        assertEquals(7L, PeriodCalculator.week(wednesday).days)
    }

    @Test
    fun `quarter and year`() {
        val date = LocalDate.of(2024, 8, 20)
        val quarter = PeriodCalculator.quarter(date)
        assertEquals(LocalDate.of(2024, 7, 1), quarter.start)
        assertEquals(LocalDate.of(2024, 9, 30), quarter.endInclusive)
        val year = PeriodCalculator.year(date)
        assertEquals(LocalDate.of(2024, 1, 1), year.start)
        assertEquals(LocalDate.of(2024, 12, 31), year.endInclusive)
    }

    @Test
    fun `last months returns oldest first`() {
        val months = PeriodCalculator.lastMonths(LocalDate.of(2024, 3, 5), 3)
        assertEquals(
            listOf(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1), LocalDate.of(2024, 3, 1)),
            months.map { it.start },
        )
    }

    @Test
    fun `buckets are days for a month and months for a year`() {
        val month = PeriodCalculator.month(LocalDate.of(2024, 4, 1))
        assertEquals(30, PeriodCalculator.buckets(month).size)
        val year = PeriodCalculator.year(LocalDate.of(2024, 4, 1))
        val buckets = PeriodCalculator.buckets(year)
        assertEquals(12, buckets.size)
        assertEquals(LocalDate.of(2024, 12, 31), buckets.last().endInclusive)
    }

    @Test
    fun `shift moves anchor`() {
        val anchor = LocalDate.of(2024, 1, 31)
        assertEquals(LocalDate.of(2024, 2, 29), PeriodCalculator.shift(PeriodType.MONTH, anchor, 1))
        assertEquals(LocalDate.of(2023, 10, 31), PeriodCalculator.shift(PeriodType.QUARTER, anchor, -1))
    }

    @Test
    fun `date range contains`() {
        val range = DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31))
        assertTrue(LocalDate.of(2024, 1, 31) in range)
        assertFalse(LocalDate.of(2024, 2, 1) in range)
    }
}
