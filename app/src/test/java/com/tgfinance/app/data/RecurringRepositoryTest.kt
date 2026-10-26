package com.tgfinance.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgfinance.app.data.repository.RoomAccountRepository
import com.tgfinance.app.data.repository.RoomRecurringRepository
import com.tgfinance.app.data.repository.RoomTransactionRepository
import com.tgfinance.app.data.repository.TransactionFilter
import com.tgfinance.core.model.Frequency
import com.tgfinance.core.model.RecurringRule
import com.tgfinance.core.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RecurringRepositoryTest : DatabaseTest() {

    private val accounts by lazy { RoomAccountRepository(db, stamps, ids) }
    private val transactions by lazy { RoomTransactionRepository(db, stamps, ids) }
    private val recurring by lazy { RoomRecurringRepository(db, stamps, ids) }

    private suspend fun rent(start: LocalDate, end: LocalDate? = null): String {
        val giro = accounts.save(account("Giro"))
        return recurring.save(
            RecurringRule(
                id = "", type = TransactionType.EXPENSE, amount = 95_000, accountId = giro,
                note = "Miete", frequency = Frequency.MONTHLY, startDate = start, endDate = end,
            ),
        )
    }

    @Test
    fun catchesUpMissedOccurrencesAndAdvances() = runTest {
        val id = rent(LocalDate.of(2024, 3, 1))
        val created = recurring.processDue(LocalDate.of(2024, 5, 15))

        assertEquals(3, created)
        val dates = transactions.observe(TransactionFilter()).first().map { it.transaction.date }.sorted()
        assertEquals(listOf(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 4, 1), LocalDate.of(2024, 5, 1)), dates)
        assertEquals(LocalDate.of(2024, 6, 1), recurring.get(id)!!.nextDate)
    }

    @Test
    fun processingTwiceDoesNotDuplicate() = runTest {
        rent(LocalDate.of(2024, 5, 1))
        recurring.processDue(LocalDate.of(2024, 5, 15))
        val again = recurring.processDue(LocalDate.of(2024, 5, 15))
        assertEquals(0, again)
        assertEquals(1, transactions.observe(TransactionFilter()).first().size)
    }

    @Test
    fun bookingIdsAreDeterministicForSync() = runTest {
        val id = rent(LocalDate.of(2024, 5, 1))
        recurring.processDue(LocalDate.of(2024, 5, 1))
        val tx = transactions.observe(TransactionFilter()).first().single().transaction
        assertEquals("rec-$id-2024-05-01", tx.id)
        assertEquals(id, tx.recurringId)
    }

    @Test
    fun ruleEndsAfterEndDate() = runTest {
        val id = rent(LocalDate.of(2024, 3, 1), end = LocalDate.of(2024, 4, 15))
        val created = recurring.processDue(LocalDate.of(2024, 6, 1))
        assertEquals(2, created)
        assertFalse(recurring.get(id)!!.active)
    }
}
