package com.tgfinance.core.money

import com.tgfinance.core.model.Cents
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** Formats minor-unit amounts for display, e.g. 123456 -> "1.234,56 €". */
class MoneyFormatter(
    currencyCode: String = "EUR",
    private val locale: Locale = Locale.GERMANY,
) {
    private val currency: Currency = runCatching { Currency.getInstance(currencyCode) }
        .getOrDefault(Currency.getInstance("EUR"))

    private val format: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        currency = this@MoneyFormatter.currency
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }

    private val plainFormat: NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = true
    }

    val symbol: String get() = currency.getSymbol(locale)

    fun format(cents: Cents, showPlusSign: Boolean = false): String {
        val text = synchronized(format) { format.format(toDecimal(cents)) }
        return if (showPlusSign && cents > 0) "+$text" else text
    }

    /** Short form for chart labels: "1,2 Tsd. €" style is avoided; whole units only. */
    fun formatCompact(cents: Cents): String {
        val units = abs(cents) / 100
        val sign = if (cents < 0) "-" else ""
        return when {
            units >= 1_000_000 -> "$sign${units / 1_000_000}M $symbol"
            units >= 10_000 -> "$sign${units / 1000}k $symbol"
            else -> "$sign$units $symbol"
        }
    }

    /** Amount without currency symbol, suitable for editing ("12,50"). */
    fun formatPlain(cents: Cents): String = synchronized(plainFormat) {
        plainFormat.isGroupingUsed = false
        plainFormat.format(toDecimal(cents))
    }

    private fun toDecimal(cents: Cents): BigDecimal = BigDecimal.valueOf(cents, 2)
}

/**
 * Parses user input into cents. Accepts German ("1.234,56") and English ("1,234.56") notation,
 * with or without currency symbol. Returns null for invalid input or more than two decimals.
 */
object AmountParser {

    private val allowed = Regex("[0-9.,]+")

    fun parse(input: String): Cents? {
        val cleaned = input.trim()
            .replace(" ", "")
            .replace(" ", "")
            .replace("€", "")
            .replace("$", "")
            .replace("£", "")
            .removePrefix("+")
        if (cleaned.isEmpty() || !allowed.matches(cleaned)) return null

        val lastComma = cleaned.lastIndexOf(',')
        val lastDot = cleaned.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 -> {
                // The separator that comes last is the decimal separator.
                if (lastComma > lastDot) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            }
            lastComma >= 0 -> {
                if (cleaned.count { it == ',' } > 1) {
                    if (isThousandsGrouping(cleaned, ',')) cleaned.replace(",", "") else return null
                } else {
                    cleaned.replace(',', '.')
                }
            }
            lastDot >= 0 -> {
                val decimals = cleaned.length - lastDot - 1
                when {
                    cleaned.count { it == '.' } > 1 ->
                        if (isThousandsGrouping(cleaned, '.')) cleaned.replace(".", "") else return null
                    // "1.234" is read as a German thousands separator.
                    decimals == 3 && lastDot > 0 -> cleaned.replace(".", "")
                    else -> cleaned
                }
            }
            else -> cleaned
        }
        if (normalized.startsWith('.') && normalized.length == 1) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.scale() > 2) return null
        return value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }

    private fun isThousandsGrouping(text: String, separator: Char): Boolean {
        val parts = text.split(separator)
        return parts.first().length in 1..3 && parts.drop(1).all { it.length == 3 }
    }
}
