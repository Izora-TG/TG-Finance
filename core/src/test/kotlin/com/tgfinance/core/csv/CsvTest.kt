package com.tgfinance.core.csv

import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountType
import com.tgfinance.core.model.Category
import com.tgfinance.core.model.CategoryType
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionDetails
import com.tgfinance.core.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CsvTest {

    @Test
    fun `escapes delimiter quotes and newlines`() {
        val out = StringBuilder()
        CsvWriter(out).writeRow(listOf("a;b", "say \"hi\"", "line\nbreak", "plain"))
        assertEquals("\"a;b\";\"say \"\"hi\"\"\";\"line\nbreak\";plain\r\n", out.toString())
    }

    @Test
    fun `exports transactions with signed amounts`() {
        val account = Account("a1", "Girokonto", AccountType.CHECKING, 0, 0, "bank")
        val category = Category("c1", "Lebensmittel", CategoryType.EXPENSE, "cart", 0)
        val tx = Transaction(
            id = "t1", type = TransactionType.EXPENSE, amount = 1234, accountId = "a1",
            categoryId = "c1", date = LocalDate.of(2024, 5, 1), note = "Wocheneinkauf",
        )
        val out = StringBuilder()
        TransactionCsvExporter.export(listOf(TransactionDetails(tx, category, account, null)), "EUR", out)
        val lines = out.toString().removePrefix(TransactionCsvExporter.BOM).split("\r\n")
        assertEquals("Datum;Typ;Betrag;Währung;Kategorie;Konto;Zielkonto;Notiz", lines[0])
        assertEquals("2024-05-01;Ausgabe;-12,34;EUR;Lebensmittel;Girokonto;;Wocheneinkauf", lines[1])
    }
}
