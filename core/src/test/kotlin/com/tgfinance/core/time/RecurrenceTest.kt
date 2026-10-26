package com.tgfinance.core.time

import com.tgfinance.core.model.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {

    @Test
    fun `monthly on the 31st does not drift`() {
        val start = LocalDate.of(2024, 1, 31)
        val due = Recurrence.dueDates(start, Frequency.MONTHLY, 1, start, LocalDate.of(2024, 5, 31))
        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 31),
                LocalDate.of(2024, 4, 30),
                LocalDate.of(2024, 5, 31),
            ),
            due,
        )
    }

    @Test
    fun `next after skips to following occurrence`() {
        val start = LocalDate.of(2024, 1, 1)
        assertEquals(LocalDate.of(2024, 1, 15), Recurrence.nextAfter(start, Frequency.WEEKLY, 2, LocalDate.of(2024, 1, 1)))
        assertEquals(LocalDate.of(2024, 1, 29), Recurrence.nextAfter(start, Frequency.WEEKLY, 2, LocalDate.of(2024, 1, 20)))
        assertEquals(start, Recurrence.nextAfter(start, Frequency.DAILY, 1, LocalDate.of(2023, 12, 1)))
    }

    @Test
    fun `due dates respect end date`() {
        val start = LocalDate.of(2024, 1, 1)
        val due = Recurrence.dueDates(
            start, Frequency.DAILY, 1, start,
            today = LocalDate.of(2024, 1, 10),
            endDate = LocalDate.of(2024, 1, 3),
        )
        assertEquals(3, due.size)
    }

    @Test
    fun `nothing due in the future`() {
        val start = LocalDate.of(2030, 1, 1)
        assertTrue(Recurrence.dueDates(start, Frequency.YEARLY, 1, start, LocalDate.of(2024, 1, 1)).isEmpty())
    }

    @Test
    fun `yearly on leap day`() {
        val start = LocalDate.of(2024, 2, 29)
        assertEquals(LocalDate.of(2025, 2, 28), Recurrence.nextAfter(start, Frequency.YEARLY, 1, start))
        assertEquals(LocalDate.of(2028, 2, 29), Recurrence.occurrence(start, Frequency.YEARLY, 1, 4))
    }
}
