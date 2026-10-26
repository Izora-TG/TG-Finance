package com.tgfinance.app.data.sync

import com.tgfinance.core.sync.CredentialsRequest
import com.tgfinance.core.sync.ErrorResponse
import com.tgfinance.core.sync.PullResponse
import com.tgfinance.core.sync.PushRequest
import com.tgfinance.core.sync.PushResponse
import com.tgfinance.core.sync.SyncApi
import com.tgfinance.core.sync.SyncException
import com.tgfinance.core.sync.SyncPaths
import com.tgfinance.core.sync.TokenResponse
import com.tgfinance.core.sync.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Creates API clients; one shared OkHttp connection pool for all of them. */
@Singleton
class SyncApiFactory @Inject constructor() {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun create(baseUrl: String, token: String? = null): HttpSyncApi = HttpSyncApi(baseUrl, token, client)
}

/** REST client for the self-hosted server. The base URL is validated to be HTTPS beforehand. */
class HttpSyncApi(
    private val baseUrl: String,
    private val token: String?,
    private val client: OkHttpClient,
) : SyncApi {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun login(username: String, password: String): TokenResponse =
        post(SyncPaths.LOGIN, CredentialsRequest(username, password), CredentialsRequest.serializer(), TokenResponse.serializer())

    suspend fun register(username: String, password: String): TokenResponse =
        post(SyncPaths.REGISTER, CredentialsRequest(username, password), CredentialsRequest.serializer(), TokenResponse.serializer())

    suspend fun me(): UserInfo = execute(request(SyncPaths.ME).get().build(), UserInfo.serializer())

    override suspend fun pull(since: Long, limit: Int): PullResponse =
        execute(request("${SyncPaths.PULL}?since=$since&limit=$limit").get().build(), PullResponse.serializer())

    override suspend fun push(request: PushRequest): PushResponse =
        post(SyncPaths.PUSH, request, PushRequest.serializer(), PushResponse.serializer())

    private suspend fun <I, O> post(path: String, body: I, input: KSerializer<I>, output: KSerializer<O>): O =
        execute(request(path).post(json.encodeToString(input, body).toRequestBody(mediaType)).build(), output)

    private fun request(path: String): Request.Builder = Request.Builder()
        .url(baseUrl.trimEnd('/') + path)
        .header("Accept", "application/json")
        .apply { token?.let { header("Authorization", "Bearer $it") } }

    private suspend fun <T> execute(request: Request, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching { json.decodeFromString(ErrorResponse.serializer(), text).error }.getOrDefault("HTTP ${response.code}")
                    val kind = when (response.code) {
                        401, 403 -> SyncException.Kind.UNAUTHORIZED
                        409 -> SyncException.Kind.CONFIGURATION
                        else -> SyncException.Kind.SERVER
                    }
                    throw SyncException(kind, "${response.code}: $message")
                }
                json.decodeFromString(serializer, text)
            }
        } catch (e: IOException) {
            throw SyncException(SyncException.Kind.NETWORK, e.message ?: "network error", e)
        } catch (e: SerializationException) {
            throw SyncException(SyncException.Kind.SERVER, "Unexpected server response", e)
        } catch (e: IllegalArgumentException) {
            throw SyncException(SyncException.Kind.CONFIGURATION, "Invalid server address", e)
        }
    }
}
