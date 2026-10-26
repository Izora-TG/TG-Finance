package com.tgfinance.app.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tgfinance.app.R
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.Frequency
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateFormats {
    val medium: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM", Locale.getDefault())
    val weekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.getDefault())
    val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())
    val shortMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("LLL", Locale.getDefault())
    val dayOfMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())
}

/** "Heute", "Gestern" or e.g. "Montag, 5. Mai" – used as list section headers. */
@Composable
fun relativeDayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.minusDays(1) -> stringResource(R.string.date_yesterday)
    else -> if (date.year == today.year) {
        date.format(DateFormats.weekdayDayMonth)
    } else {
        date.format(DateFormats.medium)
    }
}

/** Human readable label of a statistics/budget period. */
fun periodLabel(type: PeriodType, range: DateRange): String = when (type) {
    PeriodType.MONTH ->
        if (range.start.dayOfMonth == 1) {
            range.start.format(DateFormats.monthYear).replaceFirstChar { it.titlecase() }
        } else {
            "${range.start.format(DateFormats.dayMonth)} – ${range.endInclusive.format(DateFormats.dayMonth)}"
        }
    PeriodType.YEAR -> if (range.start.dayOfYear == 1) range.start.year.toString() else rangeLabel(range)
    else -> rangeLabel(range)
}

fun rangeLabel(range: DateRange): String =
    "${range.start.format(DateFormats.dayMonth)} – ${range.endInclusive.format(DateFormats.medium)}"

@Composable
fun accountTypeLabel(type: AccountType): String = stringResource(
    when (type) {
        AccountType.CHECKING -> R.string.account_type_checking
        AccountType.CASH -> R.string.account_type_cash
        AccountType.SAVINGS -> R.string.account_type_savings
        AccountType.CREDIT_CARD -> R.string.account_type_credit_card
        AccountType.INVESTMENT -> R.string.account_type_investment
        AccountType.OTHER -> R.string.account_type_other
    },
)

@Composable
fun frequencyLabel(frequency: Frequency, interval: Int): String = if (interval <= 1) {
    stringResource(
        when (frequency) {
            Frequency.DAILY -> R.string.frequency_daily
            Frequency.WEEKLY -> R.string.frequency_weekly
            Frequency.MONTHLY -> R.string.frequency_monthly
            Frequency.YEARLY -> R.string.frequency_yearly
        },
    )
} else {
    stringResource(
        when (frequency) {
            Frequency.DAILY -> R.string.frequency_every_n_days
            Frequency.WEEKLY -> R.string.frequency_every_n_weeks
            Frequency.MONTHLY -> R.string.frequency_every_n_months
            Frequency.YEARLY -> R.string.frequency_every_n_years
        },
        interval,
    )
}
