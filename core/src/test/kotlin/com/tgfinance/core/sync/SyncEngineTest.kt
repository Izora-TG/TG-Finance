package com.tgfinance.core.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class SyncEngineTest {

    private val server = FakeSyncServer()
    private val phone = FakeLocalStore("device-a")
    private val tablet = FakeLocalStore("device-b")

    private fun engine(store: FakeLocalStore, cipher: PayloadCipher? = null, pageSize: Int = 500) =
        SyncEngine(server.api(), store, cipher, pageSize, clock = { 42L })

    @Test
    fun `local changes are pushed and arrive on the other device`() = runTest {
        phone.edit("transaction", "t1", "{\"amount\":100}", at = 1000)

        val first = engine(phone).sync()
        assertEquals(1, first.pushed)
        assertTrue(phone.dirtyRecords().isEmpty())

        val second = engine(tablet).sync()
        assertEquals(1, second.pulled)
        assertEquals("{\"amount\":100}", tablet.payload("transaction", "t1"))
    }

    @Test
    fun `own pushed records are not re-applied on next pull`() = runTest {
        phone.edit("account", "a1", "x", at = 1000)
        engine(phone).sync()
        val report = engine(phone).sync()
        assertEquals(0, report.pulled)
        assertEquals(0, report.pushed)
    }

    @Test
    fun `conflict newer local edit wins and is pushed`() = runTest {
        phone.edit("category", "c1", "old", at = 1000)
        engine(phone).sync()
        engine(tablet).sync()

        // Both devices edit while offline, the tablet later.
        phone.edit("category", "c1", "phone", at = 2000)
        tablet.edit("category", "c1", "tablet", at = 3000)

        engine(phone).sync()
        val report = engine(tablet).sync()

        assertEquals(1, report.conflicts.size)
        assertEquals(ConflictWinner.LOCAL, report.conflicts.single().winner)
        assertEquals("tablet", server.get(RecordKey("category", "c1"))!!.payload)

        engine(phone).sync()
        assertEquals("tablet", phone.payload("category", "c1"))
        assertEquals(1, tablet.conflicts.size)
    }

    @Test
    fun `conflict newer remote edit wins over older local edit`() = runTest {
        phone.edit("budget", "b1", "base", at = 1000)
        engine(phone).sync()
        engine(tablet).sync()

        tablet.edit("budget", "b1", "tablet", at = 3000)
        engine(tablet).sync()
        phone.edit("budget", "b1", "phone", at = 2000)

        val report = engine(phone).sync()
        assertEquals(ConflictWinner.REMOTE, report.conflicts.single().winner)
        assertEquals("tablet", phone.payload("budget", "b1"))
        assertTrue(phone.dirtyRecords().isEmpty())
        assertEquals("tablet", server.get(RecordKey("budget", "b1"))!!.payload)
    }

    @Test
    fun `server rejects stale push and returns winning version`() = runTest {
        server.seed(SyncRecord("goal", "g1", 5000, false, "device-b", "server"))
        // The phone has never pulled; simulate that by pushing via a fresh cursor ahead of the server.
        phone.saveCursor(Long.MAX_VALUE)
        phone.edit("goal", "g1", "phone", at = 4000)

        val report = engine(phone).sync()
        assertEquals(0, report.pushed)
        assertEquals(ConflictWinner.REMOTE, report.conflicts.single().winner)
        assertEquals("server", phone.payload("goal", "g1"))
        assertEquals(listOf(RecordKey("goal", "g1")), server.conflicts)
    }

    @Test
    fun `equal timestamps are resolved by device id on every participant`() = runTest {
        phone.edit("account", "a1", "phone", at = 1000)
        tablet.edit("account", "a1", "tablet", at = 1000)
        engine(phone).sync()
        engine(tablet).sync()
        engine(phone).sync()
        // "device-b" > "device-a", so the tablet wins everywhere.
        assertEquals("tablet", server.get(RecordKey("account", "a1"))!!.payload)
        assertEquals("tablet", phone.payload("account", "a1"))
        assertEquals("tablet", tablet.payload("account", "a1"))
    }

    @Test
    fun `soft deletes are synced`() = runTest {
        phone.edit("transaction", "t1", "x", at = 1000)
        engine(phone).sync()
        engine(tablet).sync()
        phone.edit("transaction", "t1", "x", at = 2000, deleted = true)
        engine(phone).sync()
        engine(tablet).sync()
        assertTrue(tablet.rows[RecordKey("transaction", "t1")]!!.record.deleted)
    }

    @Test
    fun `pull is paged`() = runTest {
        repeat(25) { phone.edit("transaction", "t$it", "p$it", at = 1000L + it) }
        engine(phone, pageSize = 10).sync()
        val report = engine(tablet, pageSize = 10).sync()
        assertEquals(25, report.pulled)
        assertEquals(25, tablet.rows.size)
    }

    @Test
    fun `own echo after a further local edit is no conflict`() = runTest {
        phone.edit("category", "c1", "v1", at = 1000)
        engine(phone).sync()
        // Simulate a lost cursor update: the next pull delivers our own push again.
        phone.saveCursor(0)
        phone.edit("category", "c1", "v2", at = 2000)
        val report = engine(phone).sync()
        assertTrue(report.conflicts.isEmpty())
        assertEquals("v2", server.get(RecordKey("category", "c1"))!!.payload)
    }

    @Test
    fun `edit made during push is not marked clean`() = runTest {
        phone.edit("account", "a1", "v1", at = 1000)
        val pushed = phone.dirtyRecords().single()
        phone.edit("account", "a1", "v2", at = 2000)
        phone.markClean(pushed)
        assertEquals(1, phone.dirtyRecords().size)
    }

    @Test
    fun `end to end encryption hides payload from server`() = runTest {
        val key = KeyDerivation.deriveKey("geheim".toCharArray(), ByteArray(16) { 1 }, iterations = 1000)
        phone.edit("transaction", "t1", "{\"note\":\"Miete\"}", at = 1000)
        engine(phone, AesGcmPayloadCipher(key)).sync()

        val stored = server.get(RecordKey("transaction", "t1"))!!.payload
        assertTrue(PayloadCipher.isEncrypted(stored))
        assertFalse(stored.contains("Miete"))
        // Local data stays readable.
        assertEquals("{\"note\":\"Miete\"}", phone.payload("transaction", "t1"))

        val sameKey = KeyDerivation.deriveKey("geheim".toCharArray(), ByteArray(16) { 1 }, iterations = 1000)
        engine(tablet, AesGcmPayloadCipher(sameKey)).sync()
        assertEquals("{\"note\":\"Miete\"}", tablet.payload("transaction", "t1"))
    }

    @Test
    fun `wrong encryption password fails with encryption error`() = runTest {
        val key = KeyDerivation.deriveKey("richtig".toCharArray(), ByteArray(16), iterations = 1000)
        phone.edit("transaction", "t1", "secret", at = 1000)
        engine(phone, AesGcmPayloadCipher(key)).sync()

        val wrong = KeyDerivation.deriveKey("falsch".toCharArray(), ByteArray(16), iterations = 1000)
        val error = runCatching { engine(tablet, AesGcmPayloadCipher(wrong)).sync() }.exceptionOrNull()
        assertEquals(SyncException.Kind.ENCRYPTION, (error as SyncException).kind)

        val missing = runCatching { engine(tablet).sync() }.exceptionOrNull()
        assertEquals(SyncException.Kind.ENCRYPTION, (missing as SyncException).kind)
    }

    @Test
    fun `cipher uses random iv`() {
        val cipher = AesGcmPayloadCipher(SecretKeySpec(ByteArray(32), "AES"))
        assertNotEquals(cipher.encrypt("a"), cipher.encrypt("a"))
        assertEquals("a", cipher.decrypt(cipher.encrypt("a")))
    }
}
