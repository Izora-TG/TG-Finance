package com.tgfinance.app.ui

import app.cash.turbine.test
import com.tgfinance.app.R
import com.tgfinance.app.ui.transactions.TransactionEditViewModel
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionEditViewModelTest : ViewModelTestBase() {

    private lateinit var viewModel: TransactionEditViewModel

    @Before
    fun setUp() {
        seed()
        viewModel = TransactionEditViewModel(transactions, accounts, categories, settings, time)
    }

    @Test
    fun quickEntrySavesExpenseInThreeSteps() = runTest {
        viewModel.uiState.test {
            viewModel.load(null, null, null)
            viewModel.setAmount("12,50")
            viewModel.setCategory("food")
            viewModel.save()
            val done = expectMostRecentItem()
            assertTrue(done.done)
        }
        val saved = store.transactions.value.single()
        assertEquals(TransactionType.EXPENSE, saved.type)
        assertEquals(1250L, saved.amount)
        assertEquals("giro", saved.accountId)
        assertEquals(time.today, saved.date)
        assertEquals("giro", settings.state.value.lastAccountId)
    }

    @Test
    fun usesLastAccountForNewBookings() = runTest {
        settings.setLastAccountId("cash")
        viewModel.uiState.test {
            viewModel.load(null, null, null)
            assertEquals("cash", expectMostRecentItem().form.accountId)
        }
    }

    @Test
    fun invalidAmountShowsError() = runTest {
        viewModel.uiState.test {
            viewModel.load(null, null, null)
            viewModel.setAmount("abc")
            viewModel.setCategory("food")
            viewModel.save()
            val state = expectMostRecentItem()
            assertEquals(R.string.error_amount_invalid, state.error)
            assertFalse(state.done)
        }
        assertTrue(store.transactions.value.isEmpty())
    }

    @Test
    fun switchingTypeResetsCategoryAndPreparesTransfer() = runTest {
        viewModel.uiState.test {
            viewModel.load(null, null, null)
            viewModel.setCategory("food")
            viewModel.setType(TransactionType.TRANSFER)
            val state = expectMostRecentItem()
            assertNull(state.form.categoryId)
            assertEquals("cash", state.form.toAccountId)
            assertFalse(state.form.toAccountId == state.form.accountId)
        }
    }

    @Test
    fun editingLoadsExistingValues() = runTest {
        store.transactions.value = listOf(
            Transaction("t1", TransactionType.INCOME, 285_000, "giro", categoryId = "salary", date = time.today, note = "Mai"),
        )
        viewModel.uiState.test {
            viewModel.load("t1", null, null)
            val state = expectMostRecentItem()
            assertFalse(state.isNew)
            assertEquals("2850,00", state.form.amountText)
            assertEquals(TransactionType.INCOME, state.form.type)
            assertEquals(listOf("salary"), state.categories.map { it.id })
        }
    }

    @Test
    fun deleteRemovesTransaction() = runTest {
        store.transactions.value = listOf(Transaction("t1", TransactionType.EXPENSE, 100, "giro", categoryId = "food", date = time.today))
        viewModel.uiState.test {
            viewModel.load("t1", null, null)
            viewModel.delete()
            assertTrue(expectMostRecentItem().done)
        }
        assertTrue(store.transactions.value.isEmpty())
    }
}
