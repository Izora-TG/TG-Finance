package com.tgfinance.app.data.sync

import com.tgfinance.app.data.local.TgDatabase
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.TimeProvider
import com.tgfinance.core.sync.AesGcmPayloadCipher
import com.tgfinance.core.sync.KeyDerivation
import com.tgfinance.core.sync.PayloadCipher
import com.tgfinance.core.sync.ServerUrl
import com.tgfinance.core.sync.SyncException
import com.tgfinance.core.sync.SyncEngine
import com.tgfinance.core.sync.SyncReport
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** What the UI shows about sync (unobtrusively). */
data class SyncStatus(
    val enabled: Boolean = false,
    val running: Boolean = false,
    val online: Boolean = true,
    val lastSyncAt: Long = 0L,
    val error: SyncException.Kind? = null,
    val serverUrl: String = "",
    val username: String = "",
    val encrypted: Boolean = false,
)

/** Abstraction used by view models (fakeable in tests). */
interface SyncController {
    val status: Flow<SyncStatus>
    /** Runs a sync if it is enabled; never throws. */
    suspend fun syncNow(): Result<SyncReport?>
}

@Singleton
class SyncManager @Inject constructor(
    private val db: TgDatabase,
    private val settings: SettingsRepository,
    private val secrets: SecretStorage,
    private val apiFactory: SyncApiFactory,
    network: NetworkMonitor,
    private val time: TimeProvider,
) : SyncController {

    private val running = MutableStateFlow(false)
    private val mutex = Mutex()

    override val status: Flow<SyncStatus> = combine(settings.syncSettings, running, network.isOnline) { s, isRunning, online ->
        SyncStatus(
            enabled = s.enabled,
            running = isRunning,
            online = online,
            lastSyncAt = s.lastSyncAt,
            error = s.lastError?.let { runCatching { SyncException.Kind.valueOf(it) }.getOrNull() },
            serverUrl = s.serverUrl,
            username = s.username,
            encrypted = s.e2eEnabled,
        )
    }

    /**
     * Logs in (or registers) and enables sync. All existing local data is queued for upload.
     * Throws [SyncException] on failure.
     */
    suspend fun signIn(serverUrl: String, username: String, password: String, register: Boolean) {
        val url = when (val result = ServerUrl.normalize(serverUrl)) {
            is ServerUrl.Result.Valid -> result.url
            ServerUrl.Result.NotHttps -> throw SyncException(SyncException.Kind.CONFIGURATION, "HTTPS required")
            else -> throw SyncException(SyncException.Kind.CONFIGURATION, "Invalid server address")
        }
        val anonymous = apiFactory.create(url)
        val token = if (register) anonymous.register(username.trim(), password) else anonymous.login(username.trim(), password)
        val info = apiFactory.create(url, token.token).me()
        secrets.put(SecretStorage.TOKEN, token.token)
        secrets.put(SecretStorage.E2E_KEY, null)
        db.withTransaction { syncDaos().forEach { it.markAllDirty() } }
        settings.updateSync {
            it.copy(
                enabled = true,
                serverUrl = url,
                username = info.username,
                cursor = 0L,
                lastSyncAt = 0L,
                lastError = null,
                e2eEnabled = false,
                e2eSalt = info.e2eSalt,
            )
        }
    }

    suspend fun signOut() {
        secrets.put(SecretStorage.TOKEN, null)
        secrets.put(SecretStorage.E2E_KEY, null)
        settings.updateSync { it.copy(enabled = false, cursor = 0L, lastError = null, e2eEnabled = false) }
    }

    /**
     * Enables end-to-end encryption. The key is derived from [password] and the per-user salt;
     * only the derived key is stored (in the Keystore-backed storage), never the password.
     * All records are re-uploaded encrypted, replacing the plain copies on the server.
     */
    suspend fun enableEncryption(password: CharArray) {
        val salt = settings.syncSettings.first().e2eSalt
            ?: throw SyncException(SyncException.Kind.CONFIGURATION, "Not signed in")
        val key = withContext(Dispatchers.Default) { KeyDerivation.deriveKey(password, Base64.getDecoder().decode(salt)) }
        password.fill(' ')
        secrets.put(SecretStorage.E2E_KEY, Base64.getEncoder().encodeToString(key.encoded))
        val deviceId = settings.deviceId()
        db.withTransaction { syncDaos().forEach { it.touchAll(time.now(), deviceId) } }
        settings.updateSync { it.copy(e2eEnabled = true, lastError = null) }
    }

    suspend fun disableEncryption() {
        secrets.put(SecretStorage.E2E_KEY, null)
        val deviceId = settings.deviceId()
        db.withTransaction { syncDaos().forEach { it.touchAll(time.now(), deviceId) } }
        settings.updateSync { it.copy(e2eEnabled = false, lastError = null) }
    }

    /**
     * Joins an account whose data is already encrypted (e.g. on a second device): stores the
     * key without re-uploading anything.
     */
    suspend fun unlockEncryption(password: CharArray) {
        val salt = settings.syncSettings.first().e2eSalt
            ?: throw SyncException(SyncException.Kind.CONFIGURATION, "Not signed in")
        val key = withContext(Dispatchers.Default) { KeyDerivation.deriveKey(password, Base64.getDecoder().decode(salt)) }
        password.fill(' ')
        secrets.put(SecretStorage.E2E_KEY, Base64.getEncoder().encodeToString(key.encoded))
        settings.updateSync { it.copy(e2eEnabled = true, lastError = null, cursor = 0L) }
    }

    override suspend fun syncNow(): Result<SyncReport?> = mutex.withLock {
        val config = settings.syncSettings.first()
        if (!config.enabled) return Result.success(null)
        running.value = true
        try {
            val token = secrets.get(SecretStorage.TOKEN)
                ?: throw SyncException(SyncException.Kind.UNAUTHORIZED, "No token")
            val cipher: PayloadCipher? = if (config.e2eEnabled) {
                val key = secrets.get(SecretStorage.E2E_KEY)
                    ?: throw SyncException(SyncException.Kind.ENCRYPTION, "Encryption password required")
                AesGcmPayloadCipher(SecretKeySpec(Base64.getDecoder().decode(key), "AES"))
            } else {
                null
            }
            val store = RoomSyncStore(db, settings, settings.deviceId())
            val report = SyncEngine(apiFactory.create(config.serverUrl, token), store, cipher, clock = time::now).sync()
            settings.updateSync { it.copy(lastSyncAt = time.now(), lastError = null) }
            Result.success(report)
        } catch (e: SyncException) {
            settings.updateSync { it.copy(lastError = e.kind.name) }
            Result.failure(e)
        } finally {
            running.value = false
        }
    }

    private fun syncDaos() = listOf(
        db.accountDao(), db.categoryDao(), db.transactionDao(), db.budgetDao(), db.recurringDao(), db.goalDao(),
    )
}
