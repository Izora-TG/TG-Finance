package com.tgfinance.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgfinance.app.data.repository.RoomAccountRepository
import com.tgfinance.app.data.repository.RoomCategoryRepository
import com.tgfinance.app.data.repository.RoomTransactionRepository
import com.tgfinance.app.data.repository.TransactionFilter
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.time.DateRange
import com.tgfinance.core.time.PeriodCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TransactionRepositoryTest : DatabaseTest() {

    private val accounts by lazy { RoomAccountRepository(db, stamps, ids) }
    private val categories by lazy { RoomCategoryRepository(db, stamps, ids) }
    private val transactions by lazy { RoomTransactionRepository(db, stamps, ids) }
    private val may = LocalDate.of(2024, 5, 10)

    private suspend fun expense(amount: Long, accountId: String, categoryId: String?, date: LocalDate = may, note: String = "") =
        transactions.save(Transaction("", TransactionType.EXPENSE, amount, accountId, categoryId = categoryId, date = date, note = note))

    @Test
    fun saveAssignsIdAndSyncMetadata() = runTest {
        val acc = accounts.save(account("Giro"))
        val id = expense(1000, acc, null)

        val entity = db.transactionDao().findAny(id)!!
        assertTrue(entity.dirty)
        assertEquals("test-device", entity.deviceId)
        assertTrue(entity.updatedAt > 0)
        assertEquals(entity.updatedAt, entity.createdAt)
    }

    @Test
    fun deleteIsSoftAndHidesTransaction() = runTest {
        val acc = accounts.save(account("Giro"))
        val id = expense(1000, acc, null)
        transactions.delete(id)

        assertNull(transactions.get(id))
        val tombstone = db.transactionDao().findAny(id)!!
        assertTrue(tombstone.deleted)
        assertTrue(tombstone.dirty)
        assertTrue(transactions.observe(TransactionFilter()).first().isEmpty())
    }

    @Test
    fun filterBySearchTypeAccountAndRange() = runTest {
        val giro = accounts.save(account("Giro"))
        val cash = accounts.save(account("Bargeld"))
        val food = categories.save(category("Lebensmittel"))
        expense(1000, giro, food, note = "Wocheneinkauf")
        expense(500, cash, null, note = "Kiosk")
        transactions.save(Transaction("", TransactionType.INCOME, 300_000, giro, date = may.minusMonths(1)))

        assertEquals(1, transactions.observe(TransactionFilter(query = "woche")).first().size)
        assertEquals(1, transactions.observe(TransactionFilter(query = "lebensmit")).first().size) // category name
        assertEquals(1, transactions.observe(TransactionFilter(query = "bargeld")).first().size) // account name
        assertEquals(1, transactions.observe(TransactionFilter(type = TransactionType.INCOME)).first().size)
        assertEquals(2, transactions.observe(TransactionFilter(accountId = giro)).first().size)
        val mayRange = PeriodCalculator.month(may)
        assertEquals(2, transactions.observe(TransactionFilter(range = mayRange)).first().size)
    }

    @Test
    fun totalsAndCategoryTotals() = runTest {
        val giro = accounts.save(account("Giro"))
        val food = categories.save(category("Lebensmittel"))
        val fun_ = categories.save(category("Freizeit"))
        expense(1000, giro, food)
        expense(2500, giro, fun_)
        expense(700, giro, food)
        transactions.save(Transaction("", TransactionType.INCOME, 5000, giro, date = may))

        val range = PeriodCalculator.month(may)
        val totals = transactions.observeTotals(range).first()
        assertEquals(5000, totals.income)
        assertEquals(4200, totals.expense)

        val byCategory = transactions.observeCategoryTotals(range, TransactionType.EXPENSE).first()
        assertEquals(listOf("Freizeit" to 2500L, "Lebensmittel" to 1700L), byCategory.map { it.category.name to it.total })
    }

    @Test
    fun transferRequiresTwoAccountsAndDropsCategory() = runTest {
        val giro = accounts.save(account("Giro"))
        val savings = accounts.save(account("Spar"))
        val id = transactions.save(
            Transaction("", TransactionType.TRANSFER, 1000, giro, toAccountId = savings, categoryId = "x", date = may),
        )
        assertNull(transactions.get(id)!!.categoryId)
        val error = runCatching {
            transactions.save(Transaction("", TransactionType.TRANSFER, 1000, giro, toAccountId = giro, date = may))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun bucketsAndBalanceTrend() = runTest {
        val giro = accounts.save(account("Giro", initial = 10_000))
        expense(1000, giro, null, date = LocalDate.of(2024, 4, 30))
        expense(2000, giro, null, date = LocalDate.of(2024, 5, 2))
        transactions.save(Transaction("", TransactionType.INCOME, 500, giro, date = LocalDate.of(2024, 5, 3)))

        val buckets = PeriodCalculator.buckets(DateRange(LocalDate.of(2024, 5, 1), LocalDate.of(2024, 5, 3)))
        val totals = transactions.observeBucketTotals(buckets).first()
        assertEquals(listOf(0L, 2000L, 0L), totals.map { it.expense })
        assertEquals(listOf(0L, 0L, 500L), totals.map { it.income })

        val trend = transactions.observeBalanceTrend(buckets).first()
        assertEquals(listOf(9_000L, 7_000L, 7_500L), trend.map { it.balance })
    }

    @Test
    fun editingKeepsCreatedAt() = runTest {
        val giro = accounts.save(account("Giro"))
        val id = expense(1000, giro, null)
        val created = db.transactionDao().findAny(id)!!.createdAt
        transactions.save(transactions.get(id)!!.copy(amount = 2000))
        val updated = db.transactionDao().findAny(id)!!
        assertEquals(created, updated.createdAt)
        assertEquals(2000, updated.amount)
        assertTrue(updated.updatedAt > created)
    }
}
