package com.tgfinance.server

import com.tgfinance.core.sync.ConflictWinner
import com.tgfinance.core.sync.CredentialsRequest
import com.tgfinance.core.sync.LocalRecordState
import com.tgfinance.core.sync.LocalSyncStore
import com.tgfinance.core.sync.PullResponse
import com.tgfinance.core.sync.PushRequest
import com.tgfinance.core.sync.PushResponse
import com.tgfinance.core.sync.RecordKey
import com.tgfinance.core.sync.SyncApi
import com.tgfinance.core.sync.SyncConflict
import com.tgfinance.core.sync.SyncEngine
import com.tgfinance.core.sync.SyncPaths
import com.tgfinance.core.sync.SyncRecord
import com.tgfinance.core.sync.TokenResponse
import com.tgfinance.core.sync.UserInfo
import com.tgfinance.core.sync.LastWriteWins
import com.tgfinance.core.sync.version
import com.tgfinance.server.auth.LoginRateLimiter
import com.tgfinance.server.auth.PasswordHasher
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ServerTest {

    private fun config(allowRegistration: Boolean = true) = ServerConfig(
        databaseUrl = "jdbc:sqlite:" + File(Files.createTempDirectory("tgf").toFile(), "test.db").absolutePath,
        jwtSecret = "test-secret-that-is-long-enough-123456",
        allowRegistration = allowRegistration,
        maxLoginAttempts = 3,
    )

    private fun serverTest(config: ServerConfig = config(), block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(config) }
            val client = createClient { install(ContentNegotiation) { json() } }
            block(client)
        }

    private suspend fun HttpClient.register(name: String, password: String = "very-secret-pw"): TokenResponse =
        post(SyncPaths.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest(name, password))
        }.body()

    private fun record(id: String, at: Long, device: String, payload: String = "p", type: String = "transaction") =
        SyncRecord(type, id, at, false, device, payload)

    private suspend fun HttpClient.push(token: String, vararg records: SyncRecord): PushResponse =
        post(SyncPaths.PUSH) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("dev", records.toList()))
        }.body()

    private suspend fun HttpClient.pull(token: String, since: Long = 0, limit: Int = 500): PullResponse =
        get(SyncPaths.PULL) {
            bearerAuth(token)
            parameter("since", since)
            parameter("limit", limit)
        }.body()

    @Test
    fun healthEndpoint() = serverTest { client ->
        assertEquals(HttpStatusCode.OK, client.get(SyncPaths.HEALTH).status)
    }

    @Test
    fun registerLoginAndMe() = serverTest { client ->
        val registered = client.register("alice")
        assertTrue(registered.token.isNotBlank())

        val login = client.post(SyncPaths.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest("alice", "very-secret-pw"))
        }
        assertEquals(HttpStatusCode.OK, login.status)
        val token = login.body<TokenResponse>().token

        val me = client.get(SyncPaths.ME) { bearerAuth(token) }.body<UserInfo>()
        assertEquals("alice", me.username)
        assertTrue(me.e2eSalt.isNotBlank())
    }

    @Test
    fun rejectsDuplicateWeakAndDisabledRegistration() = serverTest { client ->
        client.register("bob")
        val duplicate = client.post(SyncPaths.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest("bob", "another-password"))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val weak = client.post(SyncPaths.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest("carol", "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, weak.status)
    }

    @Test
    fun registrationCanBeDisabled() = serverTest(config(allowRegistration = false)) { client ->
        val response = client.post(SyncPaths.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest("dave", "very-secret-pw"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun loginIsRateLimited() = serverTest { client ->
        client.register("eve")
        repeat(3) {
            val wrong = client.post(SyncPaths.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody(CredentialsRequest("eve", "wrong-password"))
            }
            assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        }
        val locked = client.post(SyncPaths.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(CredentialsRequest("eve", "very-secret-pw"))
        }
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertTrue(locked.headers["Retry-After"]!!.toInt() > 0)
    }

    @Test
    fun syncEndpointsRequireToken() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get(SyncPaths.PULL).status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(SyncPaths.ME) { bearerAuth("garbage") }.status)
    }

    @Test
    fun pushPullAndCursor() = serverTest { client ->
        val token = client.register("frank").token
        val first = client.push(token, record("t1", 1000, "a"), record("t2", 1000, "a"))
        assertEquals(2, first.accepted.size)

        val all = client.pull(token)
        assertEquals(2, all.records.size)
        assertEquals(2L, all.cursor)

        client.push(token, record("t1", 2000, "a", payload = "changed"))
        val delta = client.pull(token, since = all.cursor)
        assertEquals(listOf("changed"), delta.records.map { it.payload })
    }

    @Test
    fun pullIsPaged() = serverTest { client ->
        val token = client.register("gina").token
        client.push(token, *Array(7) { record("t$it", 1000L + it, "a") })
        val page1 = client.pull(token, limit = 5)
        assertEquals(5, page1.records.size)
        assertTrue(page1.hasMore)
        val page2 = client.pull(token, since = page1.cursor, limit = 5)
        assertEquals(2, page2.records.size)
        assertFalse(page2.hasMore)
    }

    @Test
    fun olderWriteIsRejectedWithServerVersion() = serverTest { client ->
        val token = client.register("hank").token
        client.push(token, record("t1", 2000, "a", payload = "new"))
        val response = client.push(token, record("t1", 1000, "b", payload = "old"))
        assertTrue(response.accepted.isEmpty())
        assertEquals("new", response.rejected.single().payload)
        // Equal timestamps: higher device id wins on server and clients alike.
        val tie = client.push(token, record("t1", 2000, "z", payload = "tie"))
        assertEquals(1, tie.accepted.size)
    }

    @Test
    fun usersAreIsolated() = serverTest { client ->
        val ida = client.register("ida").token
        val jan = client.register("jan").token
        client.push(ida, record("t1", 1000, "a", payload = "secret"))
        assertTrue(client.pull(jan).records.isEmpty())
    }

    @Test
    fun rejectsUnknownTypes() = serverTest { client ->
        val token = client.register("kim").token
        val response = client.post(SyncPaths.PUSH) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(PushRequest("dev", listOf(record("x", 1, "a", type = "evil"))))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    /** Two devices with real sync engines talking to the real server. */
    @Test
    fun twoDevicesSyncAndResolveConflicts() = serverTest { client ->
        val token = client.register("lena").token
        val api = object : SyncApi {
            override suspend fun pull(since: Long, limit: Int) = client.pull(token, since, limit)
            override suspend fun push(request: PushRequest): PushResponse = client.post(SyncPaths.PUSH) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
        val phone = MemoryStore("device-a")
        val tablet = MemoryStore("device-b")

        phone.edit("category", "c1", "Lebensmittel", 1000)
        SyncEngine(api, phone).sync()
        SyncEngine(api, tablet).sync()
        assertEquals("Lebensmittel", tablet.payload("category", "c1"))

        // Offline edits on both devices; the tablet's is newer.
        phone.edit("category", "c1", "Essen", 2000)
        tablet.edit("category", "c1", "Supermarkt", 3000)
        val phoneReport = SyncEngine(api, phone).sync()
        val tabletReport = SyncEngine(api, tablet).sync()
        SyncEngine(api, phone).sync()

        assertTrue(phoneReport.conflicts.isEmpty())
        assertEquals(ConflictWinner.LOCAL, tabletReport.conflicts.single().winner)
        assertEquals("Supermarkt", phone.payload("category", "c1"))
        assertEquals("Supermarkt", tablet.payload("category", "c1"))
        assertTrue(phone.conflicts.isEmpty())
        assertEquals(1, tablet.conflicts.size)
    }

    @Test
    fun passwordHashing() {
        val hasher = PasswordHasher(memoryKiB = 1024, iterations = 1)
        val hash = hasher.hash("correct horse")
        assertTrue(hash.startsWith("\$argon2id\$v=19\$"))
        assertTrue(hasher.verify("correct horse", hash))
        assertFalse(hasher.verify("wrong", hash))
        assertNotEquals(hash, hasher.hash("correct horse"))
        assertFalse(hasher.verify("x", "not-a-hash"))
    }

    @Test
    fun rateLimiterLocksAndExpires() {
        var now = 0L
        val limiter = LoginRateLimiter(maxAttempts = 2, lockMillis = 1000, clock = { now })
        limiter.recordFailure("k")
        assertEquals(0L, limiter.retryAfter("k"))
        limiter.recordFailure("k")
        assertEquals(1000L, limiter.retryAfter("k"))
        now = 1500
        assertEquals(0L, limiter.retryAfter("k"))
    }
}

private class MemoryStore(override val deviceId: String) : LocalSyncStore {
    val rows = linkedMapOf<RecordKey, Pair<SyncRecord, Boolean>>()
    val conflicts = mutableListOf<SyncConflict>()
    private var cursor = 0L

    fun edit(type: String, id: String, payload: String, at: Long) {
        val r = SyncRecord(type, id, at, false, deviceId, payload)
        rows[r.key] = r to true
    }

    fun payload(type: String, id: String) = rows[RecordKey(type, id)]?.first?.payload

    override suspend fun cursor() = cursor
    override suspend fun saveCursor(cursor: Long) {
        this.cursor = cursor
    }
    override suspend fun state(key: RecordKey) = rows[key]?.let { LocalRecordState(it.first.version, it.second) }
    override suspend fun dirtyRecords() = rows.values.filter { it.second }.map { it.first }
    override suspend fun applyRemote(record: SyncRecord) {
        rows[record.key] = record to false
    }
    override suspend fun markClean(record: SyncRecord) {
        val current = rows[record.key] ?: return
        if (LastWriteWins.isSameVersion(current.first.version, record.version)) rows[record.key] = current.first to false
    }
    override suspend fun logConflict(conflict: SyncConflict) {
        conflicts += conflict
    }
}
