package com.tgfinance.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tgfinance.app.data.repository.RoomAccountRepository
import com.tgfinance.app.data.repository.RoomTransactionRepository
import com.tgfinance.app.data.sync.RoomSyncStore
import com.tgfinance.core.model.Transaction
import com.tgfinance.core.model.TransactionType
import com.tgfinance.core.sync.AesGcmPayloadCipher
import com.tgfinance.core.sync.ConflictWinner
import com.tgfinance.core.sync.LastWriteWins
import com.tgfinance.core.sync.PayloadCipher
import com.tgfinance.core.sync.PullResponse
import com.tgfinance.core.sync.PushRequest
import com.tgfinance.core.sync.PushResponse
import com.tgfinance.core.sync.RecordKey
import com.tgfinance.core.sync.SyncApi
import com.tgfinance.core.sync.SyncEngine
import com.tgfinance.core.sync.SyncRecord
import com.tgfinance.core.sync.version
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec

/** Minimal server with the same last-write-wins rule as the real backend. */
private class InMemoryServer : SyncApi {
    val records = linkedMapOf<RecordKey, Pair<SyncRecord, Long>>()
    private var seq = 0L

    override suspend fun pull(since: Long, limit: Int): PullResponse {
        val changed = records.values.filter { it.second > since }.sortedBy { it.second }
        val page = changed.take(limit)
        return PullResponse(page.map { it.first }, page.lastOrNull()?.second ?: since, changed.size > limit)
    }

    override suspend fun push(request: PushRequest): PushResponse {
        val accepted = mutableListOf<RecordKey>()
        val rejected = mutableListOf<SyncRecord>()
        request.records.forEach { r ->
            val existing = records[r.key]?.first
            if (existing == null || LastWriteWins.candidateWins(r.version, existing.version)) {
                records[r.key] = r to ++seq
                accepted += r.key
            } else if (LastWriteWins.isSameVersion(existing.version, r.version)) {
                accepted += r.key
            } else {
                rejected += existing
            }
        }
        return PushResponse(accepted, rejected)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoomSyncStoreTest : DatabaseTest() {

    private val accounts by lazy { RoomAccountRepository(db, stamps, ids) }
    private val transactions by lazy { RoomTransactionRepository(db, stamps, ids) }
    private val store by lazy { RoomSyncStore(db, settings, "test-device") }
    private val day = LocalDate.of(2024, 5, 10)

    @Test
    fun dirtyRowsBecomeRecordsWithoutLocalFlag() = runTest {
        val giro = accounts.save(account("Giro"))
        val records = store.dirtyRecords()
        val record = records.single()
        assertEquals("account", record.type)
        assertEquals(giro, record.id)
        assertEquals("test-device", record.deviceId)
        assertTrue(record.payload.contains("\"name\":\"Giro\""))
        assertFalse(record.payload.contains("dirty"))
    }

    @Test
    fun applyRemoteWritesCleanRowWithRemoteMetadata() = runTest {
        val giro = accounts.save(account("Giro"))
        val local = store.dirtyRecords().single()
        val remote = local.copy(
            updatedAt = local.updatedAt + 10,
            deviceId = "other",
            payload = local.payload.replace("Giro", "Hauptkonto"),
        )
        store.applyRemote(remote)
        val entity = db.accountDao().findAny(giro)!!
        assertEquals("Hauptkonto", entity.name)
        assertEquals("other", entity.deviceId)
        assertFalse(entity.dirty)
    }

    @Test
    fun markCleanOnlyForUnchangedVersion() = runTest {
        val giro = accounts.save(account("Giro"))
        val pushed = store.dirtyRecords().single()
        accounts.save(accounts.get(giro)!!.copy(name = "Neu"))
        store.markClean(pushed)
        assertTrue(db.accountDao().findAny(giro)!!.dirty)
    }

    @Test
    fun unknownTypesAndBrokenPayloadsAreIgnored() = runTest {
        store.applyRemote(SyncRecord("future-type", "x", 1, false, "d", "{}"))
        store.applyRemote(SyncRecord("account", "y", 1, false, "d", "not json"))
        assertNull(db.accountDao().findAny("y"))
    }

    @Test
    fun cursorIsPersistedInSettings() = runTest {
        store.saveCursor(42)
        assertEquals(42L, settings.sync.first().cursor)
    }

    @Test
    fun fullSyncBetweenTwoDatabasesWithConflict() = runTest {
        val server = InMemoryServer()
        val giro = accounts.save(account("Giro"))
        val txId = transactions.save(Transaction("", TransactionType.EXPENSE, 1000, giro, date = day, note = "Phone"))
        SyncEngine(server, store, clock = { 1L }).sync()
        assertTrue(store.dirtyRecords().isEmpty())

        // Second device: a separate database.
        val other = androidx.room.Room.inMemoryDatabaseBuilder(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            com.tgfinance.app.data.local.TgDatabase::class.java,
        ).allowMainThreadQueries().build()
        val otherSettings = com.tgfinance.app.fakes.FakeSettingsRepository(deviceId = "tablet")
        val otherStore = RoomSyncStore(other, otherSettings, "tablet")
        SyncEngine(server, otherStore, clock = { 1L }).sync()
        assertEquals("Phone", other.transactionDao().find(txId)!!.note)

        // Conflicting edits: the tablet edits later and wins on both devices.
        transactions.save(transactions.get(txId)!!.copy(note = "Phone edit"))
        val tabletTx = other.transactionDao().find(txId)!!
        other.transactionDao().upsert(
            tabletTx.copy(note = "Tablet edit", updatedAt = time.now + 1_000_000, deviceId = "tablet", dirty = true),
        )
        SyncEngine(server, otherStore, clock = { 2L }).sync()
        val report = SyncEngine(server, store, clock = { 3L }).sync()

        assertEquals(ConflictWinner.REMOTE, report.conflicts.single().winner)
        assertEquals("Tablet edit", db.transactionDao().find(txId)!!.note)
        assertEquals(1, db.syncConflictDao().observeRecent().first().size)
        other.close()
    }

    @Test
    fun encryptedPayloadsRoundTrip() = runTest {
        val server = InMemoryServer()
        val cipher = AesGcmPayloadCipher(SecretKeySpec(ByteArray(32) { 7 }, "AES"))
        val giro = accounts.save(account("Geheimkonto"))
        SyncEngine(server, store, cipher).sync()
        val stored = server.records[RecordKey("account", giro)]!!.first.payload
        assertTrue(PayloadCipher.isEncrypted(stored))
        assertFalse(stored.contains("Geheimkonto"))
    }
}
