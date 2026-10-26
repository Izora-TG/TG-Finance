package com.tgfinance.core.time

import com.tgfinance.core.model.Frequency
import java.time.LocalDate

/**
 * Computes the occurrences of a recurring booking. Occurrences are always derived from the
 * start date (occurrence k = start + k * interval), so a rule starting on the 31st keeps
 * falling on the last day of shorter months without drifting to the 28th.
 */
object Recurrence {

    private const val MAX_CATCH_UP = 1000

    fun occurrence(start: LocalDate, frequency: Frequency, interval: Int, index: Long): LocalDate {
        val step = interval.coerceAtLeast(1).toLong() * index
        return when (frequency) {
            Frequency.DAILY -> start.plusDays(step)
            Frequency.WEEKLY -> start.plusWeeks(step)
            Frequency.MONTHLY -> start.plusMonths(step)
            Frequency.YEARLY -> start.plusYears(step)
        }
    }

    /** First occurrence that is strictly after [date]. */
    fun nextAfter(start: LocalDate, frequency: Frequency, interval: Int, date: LocalDate): LocalDate {
        if (date.isBefore(start)) return start
        var index = estimateIndex(start, frequency, interval, date)
        while (!occurrence(start, frequency, interval, index).isAfter(date)) index++
        while (index > 0 && occurrence(start, frequency, interval, index - 1).isAfter(date)) index--
        return occurrence(start, frequency, interval, index)
    }

    /**
     * All occurrences from [nextDate] up to and including [today] (bounded by [endDate]).
     * Used by the background worker to catch up on missed bookings.
     */
    fun dueDates(
        start: LocalDate,
        frequency: Frequency,
        interval: Int,
        nextDate: LocalDate,
        today: LocalDate,
        endDate: LocalDate? = null,
    ): List<LocalDate> {
        val limit = if (endDate != null && endDate.isBefore(today)) endDate else today
        val result = mutableListOf<LocalDate>()
        var current = nextDate
        while (!current.isAfter(limit) && result.size < MAX_CATCH_UP) {
            result += current
            current = nextAfter(start, frequency, interval, current)
        }
        return result
    }

    private fun estimateIndex(start: LocalDate, frequency: Frequency, interval: Int, date: LocalDate): Long {
        val step = interval.coerceAtLeast(1)
        val units = when (frequency) {
            Frequency.DAILY -> java.time.temporal.ChronoUnit.DAYS.between(start, date)
            Frequency.WEEKLY -> java.time.temporal.ChronoUnit.WEEKS.between(start, date)
            Frequency.MONTHLY -> java.time.temporal.ChronoUnit.MONTHS.between(start, date)
            Frequency.YEARLY -> java.time.temporal.ChronoUnit.YEARS.between(start, date)
        }
        return (units / step - 1).coerceAtLeast(0)
    }
}
