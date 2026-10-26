package com.tgfinance.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class DateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!endInclusive.isBefore(start)) { "endInclusive must not be before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(endInclusive)

    val days: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1
}

enum class PeriodType { WEEK, MONTH, QUARTER, YEAR }

/**
 * Calculates accounting periods. A "month" can start on any day between 1 and 28
 * (e.g. on payday), weeks start on a configurable weekday.
 */
object PeriodCalculator {

    const val MIN_MONTH_START = 1
    const val MAX_MONTH_START = 28

    fun month(date: LocalDate, monthStartDay: Int = 1): DateRange {
        val startDay = monthStartDay.coerceIn(MIN_MONTH_START, MAX_MONTH_START)
        val start = if (date.dayOfMonth >= startDay) {
            date.withDayOfMonth(startDay)
        } else {
            date.minusMonths(1).withDayOfMonth(startDay)
        }
        return DateRange(start, start.plusMonths(1).minusDays(1))
    }

    fun week(date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): DateRange {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return DateRange(start, start.plusDays(6))
    }

    fun quarter(date: LocalDate, monthStartDay: Int = 1): DateRange {
        val month = month(date, monthStartDay)
        // Quarters are aligned to the calendar month in which the period starts.
        val startMonthIndex = (month.start.monthValue - 1) / 3 * 3 + 1
        val start = month.start.withMonth(startMonthIndex)
        return DateRange(start, start.plusMonths(3).minusDays(1))
    }

    fun year(date: LocalDate, monthStartDay: Int = 1): DateRange {
        val month = month(date, monthStartDay)
        val start = month.start.withMonth(1)
        return DateRange(start, start.plusYears(1).minusDays(1))
    }

    fun range(
        type: PeriodType,
        anchor: LocalDate,
        monthStartDay: Int = 1,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): DateRange = when (type) {
        PeriodType.WEEK -> week(anchor, firstDayOfWeek)
        PeriodType.MONTH -> month(anchor, monthStartDay)
        PeriodType.QUARTER -> quarter(anchor, monthStartDay)
        PeriodType.YEAR -> year(anchor, monthStartDay)
    }

    /** Moves an anchor date by [steps] periods of [type] (negative = into the past). */
    fun shift(type: PeriodType, anchor: LocalDate, steps: Int): LocalDate = when (type) {
        PeriodType.WEEK -> anchor.plusWeeks(steps.toLong())
        PeriodType.MONTH -> anchor.plusMonths(steps.toLong())
        PeriodType.QUARTER -> anchor.plusMonths(3L * steps)
        PeriodType.YEAR -> anchor.plusYears(steps.toLong())
    }

    /** The last [count] month periods ending with the one that contains [date], oldest first. */
    fun lastMonths(date: LocalDate, count: Int, monthStartDay: Int = 1): List<DateRange> =
        (count - 1 downTo 0).map { month(date.minusMonths(it.toLong()), monthStartDay) }

    /** Splits [range] into buckets for a bar chart: days for short ranges, months otherwise. */
    fun buckets(range: DateRange, monthStartDay: Int = 1): List<DateRange> {
        if (range.days <= 31) {
            return generateSequence(range.start) { it.plusDays(1) }
                .takeWhile { !it.isAfter(range.endInclusive) }
                .map { DateRange(it, it) }
                .toList()
        }
        val result = mutableListOf<DateRange>()
        var current = month(range.start, monthStartDay)
        while (!current.start.isAfter(range.endInclusive)) {
            val start = maxOf(current.start, range.start)
            val end = minOf(current.endInclusive, range.endInclusive)
            result += DateRange(start, end)
            current = month(current.endInclusive.plusDays(1), monthStartDay)
        }
        return result
    }
}
