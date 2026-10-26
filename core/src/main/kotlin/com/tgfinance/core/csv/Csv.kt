package com.tgfinance.core.csv

import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

/** Minimal RFC 4180 style CSV writer. Semicolon is the default because German Excel expects it. */
class CsvWriter(
    private val out: Appendable,
    private val delimiter: Char = ';',
) {
    fun writeRow(values: List<String>) {
        values.forEachIndexed { index, value ->
            if (index > 0) out.append(delimiter)
            out.append(escape(value))
        }
        out.append("\r\n")
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(' ') || value.endsWith(' ')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}

/** Column labels are passed in so they can come from Android string resources. */
data class TransactionCsvLabels(
    val date: String = "Datum",
    val type: String = "Typ",
    val amount: String = "Betrag",
    val currency: String = "Währung",
    val category: String = "Kategorie",
    val account: String = "Konto",
    val toAccount: String = "Zielkonto",
    val note: String = "Notiz",
    val income: String = "Einnahme",
    val expense: String = "Ausgabe",
    val transfer: String = "Umbuchung",
)

object TransactionCsvExporter {

    /** UTF-8 byte order mark so that Excel detects the encoding (umlauts). */
    const val BOM = "﻿"

    fun export(
        transactions: List<TransactionDetails>,
        currencyCode: String,
        out: Appendable,
        labels: TransactionCsvLabels = TransactionCsvLabels(),
        decimalComma: Boolean = true,
    ) {
        out.append(BOM)
        val writer = CsvWriter(out)
        writer.writeRow(
            listOf(
                labels.date, labels.type, labels.amount, labels.currency,
                labels.category, labels.account, labels.toAccount, labels.note,
            ),
        )
        transactions.forEach { details ->
            val tx = details.transaction
            val amount = BigDecimal.valueOf(tx.signedAmountForExport(), 2).toPlainString()
                .let { if (decimalComma) it.replace('.', ',') else it }
            writer.writeRow(
                listOf(
                    tx.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    when (tx.type) {
                        TransactionType.INCOME -> labels.income
                        TransactionType.EXPENSE -> labels.expense
                        TransactionType.TRANSFER -> labels.transfer
                    },
                    amount,
                    currencyCode,
                    details.category?.name.orEmpty(),
                    details.account?.name.orEmpty(),
                    details.toAccount?.name.orEmpty(),
                    tx.note,
                ),
            )
        }
    }

    private fun com.tgfinance.core.model.Transaction.signedAmountForExport(): Long =
        if (type == TransactionType.EXPENSE) -amount else amount
}
