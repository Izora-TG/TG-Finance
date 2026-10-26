package com.tgfinance.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tgfinance.app.R
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.AccountEntity
import com.tgfinance.app.data.local.entity.BudgetEntity
import com.tgfinance.app.data.local.entity.CategoryEntity
import com.tgfinance.app.data.local.entity.GoalEntity
import com.tgfinance.app.data.local.entity.RecurringEntity
import com.tgfinance.app.data.local.entity.TransactionEntity
import com.tgfinance.app.data.repository.StampFactory
import com.tgfinance.app.data.repository.TransactionRepository
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.core.csv.TransactionCsvExporter
import com.tgfinance.core.csv.TransactionCsvLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val createdAt: Long,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>,
    val goals: List<GoalEntity>,
) {
    val recordCount: Int
        get() = accounts.size + categories.size + transactions.size + budgets.size + recurring.size + goals.size

    companion object {
        const val FORMAT = "tg-finance-backup"
        const val VERSION = 1
    }
}

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Local JSON backup and CSV export. Restoring replaces all data; the restored rows are marked
 * as local changes so that an enabled sync distributes the restore to other devices.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: TgDatabase,
    private val transactions: TransactionRepository,
    private val settings: SettingsRepository,
    private val stamps: StampFactory,
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val list = transactions.allDetails()
        val currency = settings.settings.first().currencyCode
        val labels = TransactionCsvLabels(
            date = context.getString(R.string.csv_date),
            type = context.getString(R.string.csv_type),
            amount = context.getString(R.string.csv_amount),
            currency = context.getString(R.string.csv_currency),
            category = context.getString(R.string.csv_category),
            account = context.getString(R.string.csv_account),
            toAccount = context.getString(R.string.csv_to_account),
            note = context.getString(R.string.csv_note),
            income = context.getString(R.string.transaction_type_income),
            expense = context.getString(R.string.transaction_type_expense),
            transfer = context.getString(R.string.transaction_type_transfer),
        )
        openOutput(uri).bufferedWriter(Charsets.UTF_8).use { writer ->
            TransactionCsvExporter.export(list, currency, writer, labels)
        }
        list.size
    }

    suspend fun createBackup(): BackupFile = BackupFile(
        createdAt = System.currentTimeMillis(),
        accounts = db.accountDao().allIncludingDeleted().filterNot { it.deleted },
        categories = db.categoryDao().allIncludingDeleted().filterNot { it.deleted },
        transactions = db.transactionDao().allIncludingDeleted().filterNot { it.deleted },
        budgets = db.budgetDao().allIncludingDeleted().filterNot { it.deleted },
        recurring = db.recurringDao().allIncludingDeleted().filterNot { it.deleted },
        goals = db.goalDao().allIncludingDeleted().filterNot { it.deleted },
    )

    suspend fun exportBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = createBackup()
        openOutput(uri).use { it.write(json.encodeToString(BackupFile.serializer(), backup).toByteArray(Charsets.UTF_8)) }
        backup.recordCount
    }

    suspend fun importBackup(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = openInput(uri).use { it.readBytes().toString(Charsets.UTF_8) }
        restore(parse(text))
    }

    fun parse(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw BackupException("Not a TG-Finance backup", e)
        } catch (e: IllegalArgumentException) {
            throw BackupException("Not a TG-Finance backup", e)
        }
        if (backup.format != BackupFile.FORMAT) throw BackupException("Unknown backup format")
        if (backup.version > BackupFile.VERSION) throw BackupException("Backup was created by a newer app version")
        return backup
    }

    /** Replaces all data with the backup; returns the number of restored records. */
    suspend fun restore(backup: BackupFile): Int {
        val stamp = stamps.create()
        db.withTransaction {
            replace(db.accountDao(), backup.accounts.map { it.copy(updatedAt = stamp.now, deviceId = stamp.deviceId, deleted = false, dirty = true) }) {
                it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)
            }
            replace(db.categoryDao(), backup.categories.map { it.copy(updatedAt = stamp.now, deviceId = stamp.deviceId, deleted = false, dirty = true) }) {
                it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)
            }
            replace(db.transactionDao(), backup.transactions.map { it.copy(updatedAt = stamp.now, deviceId = stamp.deviceId, deleted = false, dirty = true) }) {
                it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)
            }
            replace(db.budgetDao(), backup.budgets.map { it.copy(updatedAt = stamp.now, deviceId = stamp.deviceId, deleted = false, dirty = true) }) {
                it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)
            }
            replace(db.recurringDao(), backup.recurring.map { it.copy(updatedAt = stamp.now, deviceId = stamp.deviceId, deleted = false, dirty = true) }) {
                it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)
            }
            replace(db.goalDao(), backup.goals.map { it.copy(updatedAt = stamp.now, deviceId = stamp.deviceId, deleted = false, dirty = true) }) {
                it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)
            }
        }
        return backup.recordCount
    }

    private suspend fun <E : com.tgfinance.app.data.local.entity.SyncEntity> replace(
        dao: com.tgfinance.app.data.local.dao.SyncDao<E>,
        incoming: List<E>,
        tombstone: (E) -> E,
    ) {
        val keep = incoming.map { it.id }.toSet()
        val obsolete = dao.allIncludingDeleted().filter { !it.deleted && it.id !in keep }.map(tombstone)
        dao.upsertAll(obsolete + incoming)
    }

    private fun openOutput(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")

    private fun openInput(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
}
