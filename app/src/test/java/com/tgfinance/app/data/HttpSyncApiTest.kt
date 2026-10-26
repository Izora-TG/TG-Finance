package com.tgfinance.app.data

import com.tgfinance.app.data.sync.HttpSyncApi
import com.tgfinance.core.sync.PushRequest
import com.tgfinance.core.sync.SyncException
import com.tgfinance.core.sync.SyncRecord
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpSyncApiTest {
    private val server = MockWebServer()
    private lateinit var api: HttpSyncApi

    @Before
    fun setUp() {
        server.start()
        // Plain HTTP only in this unit test; the app itself only accepts HTTPS URLs.
        api = HttpSyncApi(server.url("/").toString(), "token-123", OkHttpClient())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun pullSendsTokenAndCursor() = runTest {
        server.enqueue(MockResponse().setBody("""{"records":[],"cursor":7,"hasMore":false}"""))
        val response = api.pull(since = 5, limit = 100)
        assertEquals(7L, response.cursor)
        val request = server.takeRequest()
        assertEquals("/api/v1/sync/pull?since=5&limit=100", request.path)
        assertEquals("Bearer token-123", request.getHeader("Authorization"))
    }

    @Test
    fun pushSerialisesRecords() = runTest {
        server.enqueue(MockResponse().setBody("""{"accepted":[{"type":"account","id":"a1"}],"rejected":[]}"""))
        val response = api.push(PushRequest("dev", listOf(SyncRecord("account", "a1", 1, false, "dev", "{}"))))
        assertEquals("a1", response.accepted.single().id)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"deviceId\":\"dev\""))
    }

    @Test
    fun mapsHttpErrorsToSyncExceptions() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(SyncException.Kind.UNAUTHORIZED, runCatching { api.pull(0, 10) }.exceptionOrNull().kind())
        assertEquals(SyncException.Kind.SERVER, runCatching { api.pull(0, 10) }.exceptionOrNull().kind())
        server.shutdown()
        assertEquals(SyncException.Kind.NETWORK, runCatching { api.pull(0, 10) }.exceptionOrNull().kind())
    }

    private fun Throwable?.kind() = (this as SyncException).kind
}
