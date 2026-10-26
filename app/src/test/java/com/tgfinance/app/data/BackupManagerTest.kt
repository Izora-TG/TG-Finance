package com.tgfinance.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgfinance.app.data.backup.BackupException
import com.tgfinance.app.data.backup.BackupManager
import com.tgfinance.app.data.local.DefaultDataSeeder
import com.tgfinance.app.data.repository.RoomAccountRepository
import com.tgfinance.app.data.repository.RoomTransactionRepository
import com.tgfinance.app.data.settings.AppSettings
import com.tgfinance.app.fakes.FakeSettingsRepository
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupManagerTest : DatabaseTest() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val accounts by lazy { RoomAccountRepository(db, stamps, ids) }
    private val transactions by lazy { RoomTransactionRepository(db, stamps, ids) }
    private val backup by lazy { BackupManager(context, db, transactions, settings, stamps) }

    @Test
    fun backupRoundTripReplacesData() = runTest {
        val giro = accounts.save(account("Giro"))
        transactions.save(Transaction("", TransactionType.EXPENSE, 1234, giro, date = LocalDate.of(2024, 5, 1)))
        val file = backup.createBackup()
        assertEquals(2, file.recordCount)

        // Changes after the backup are rolled back by the restore.
        val later = accounts.save(account("Später"))
        backup.restore(file)

        assertTrue(db.accountDao().findAny(later)!!.deleted)
        assertEquals(1, db.transactionDao().allActive().size)
        assertTrue(db.accountDao().dirty().isNotEmpty())
    }

    @Test(expected = BackupException::class)
    fun rejectsForeignFiles() {
        backup.parse("""{"hello":"world"}""")
    }

    @Test
    fun seederIsIdempotent() = runTest {
        val fresh = FakeSettingsRepository(AppSettings(defaultsSeeded = false))
        DefaultDataSeeder(context, db, fresh).seedIfNeeded()
        val categoryCount = db.categoryDao().allIncludingDeleted().size
        assertTrue(categoryCount > 10)
        assertEquals(2, db.accountDao().count())
        // Seeded rows are not pushed – every device creates the same ones.
        assertTrue(db.categoryDao().dirty().isEmpty())

        DefaultDataSeeder(context, db, FakeSettingsRepository(AppSettings(defaultsSeeded = false))).seedIfNeeded()
        assertEquals(categoryCount, db.categoryDao().allIncludingDeleted().size)
    }
}
