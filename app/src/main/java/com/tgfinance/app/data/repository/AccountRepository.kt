package com.tgfinance.app.data.repository

import androidx.room.withTransaction
import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.local.entity.toEntity
import com.tgfinance.app.data.local.entity.toModel
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.core.model.Account
import com.tgfinance.core.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface AccountRepository {
    fun observeAccounts(): Flow<List<AccountWithBalance>>
    fun observeTotalBalance(): Flow<Long>
    suspend fun get(id: String): Account?
    /** Inserts (blank id) or updates the account and returns its id. */
    suspend fun save(account: Account): String
    /** Soft-deletes the account together with its bookings and recurring rules. */
    suspend fun delete(id: String)
}

@Singleton
class RoomAccountRepository @Inject constructor(
    private val db: TgDatabase,
    private val stamps: StampFactory,
    private val ids: IdGenerator,
) : AccountRepository {

    private val dao = db.accountDao()

    override fun observeAccounts(): Flow<List<AccountWithBalance>> =
        dao.observeWithBalances().map { rows -> rows.map { AccountWithBalance(it.account.toModel(), it.balance) } }

    override fun observeTotalBalance(): Flow<Long> =
        observeAccounts().map { accounts -> accounts.sumOf { it.balance } }

    override suspend fun get(id: String): Account? = dao.find(id)?.toModel()

    override suspend fun save(account: Account): String {
        val withId = if (account.id.isBlank()) account.copy(id = ids.newId()) else account
        dao.upsert(withId.toEntity(stamps.create()))
        return withId.id
    }

    override suspend fun delete(id: String) {
        val stamp = stamps.create()
        db.withTransaction {
            val account = dao.find(id) ?: return@withTransaction
            dao.upsert(account.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true))
            val txDao = db.transactionDao()
            txDao.allActive()
                .filter { it.accountId == id || it.toAccountId == id }
                .forEach { txDao.upsert(it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)) }
            val ruleDao = db.recurringDao()
            ruleDao.allIncludingDeleted()
                .filter { !it.deleted && (it.accountId == id || it.toAccountId == id) }
                .forEach { ruleDao.upsert(it.copy(deleted = true, updatedAt = stamp.now, deviceId = stamp.deviceId, dirty = true)) }
        }
    }
}
