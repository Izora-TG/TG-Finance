package com.tgfinance.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class AppSettings(
    val currencyCode: String = "EUR",
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Day of month on which a budget month starts (1–28), e.g. payday. */
    val monthStartDay: Int = 1,
    val biometricLock: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val lastAccountId: String? = null,
    val defaultsSeeded: Boolean = false,
)

data class SyncSettings(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val cursor: Long = 0L,
    val lastSyncAt: Long = 0L,
    val lastError: String? = null,
    val e2eEnabled: Boolean = false,
    val e2eSalt: String? = null,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val syncSettings: Flow<SyncSettings>
    suspend fun deviceId(): String
    suspend fun setCurrency(code: String)
    suspend fun setFirstDayOfWeek(day: DayOfWeek)
    suspend fun setMonthStartDay(day: Int)
    suspend fun setBiometricLock(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLastAccountId(id: String)
    suspend fun setDefaultsSeeded()
    suspend fun updateSync(transform: (SyncSettings) -> SyncSettings)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsRepository {

    private val store = context.dataStore

    private object Keys {
        val currency = stringPreferencesKey("currency")
        val firstDayOfWeek = intPreferencesKey("first_day_of_week")
        val monthStartDay = intPreferencesKey("month_start_day")
        val biometric = booleanPreferencesKey("biometric_lock")
        val theme = stringPreferencesKey("theme_mode")
        val lastAccount = stringPreferencesKey("last_account_id")
        val seeded = booleanPreferencesKey("defaults_seeded")
        val deviceId = stringPreferencesKey("device_id")

        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val syncUrl = stringPreferencesKey("sync_url")
        val syncUser = stringPreferencesKey("sync_user")
        val syncCursor = longPreferencesKey("sync_cursor")
        val syncLastAt = longPreferencesKey("sync_last_at")
        val syncLastError = stringPreferencesKey("sync_last_error")
        val syncE2e = booleanPreferencesKey("sync_e2e")
        val syncSalt = stringPreferencesKey("sync_e2e_salt")
    }

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            currencyCode = p[Keys.currency] ?: "EUR",
            firstDayOfWeek = DayOfWeek.of(p[Keys.firstDayOfWeek] ?: DayOfWeek.MONDAY.value),
            monthStartDay = p[Keys.monthStartDay] ?: 1,
            biometricLock = p[Keys.biometric] ?: false,
            themeMode = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            lastAccountId = p[Keys.lastAccount],
            defaultsSeeded = p[Keys.seeded] ?: false,
        )
    }

    override val syncSettings: Flow<SyncSettings> = store.data.map { p ->
        SyncSettings(
            enabled = p[Keys.syncEnabled] ?: false,
            serverUrl = p[Keys.syncUrl].orEmpty(),
            username = p[Keys.syncUser].orEmpty(),
            cursor = p[Keys.syncCursor] ?: 0L,
            lastSyncAt = p[Keys.syncLastAt] ?: 0L,
            lastError = p[Keys.syncLastError],
            e2eEnabled = p[Keys.syncE2e] ?: false,
            e2eSalt = p[Keys.syncSalt],
        )
    }

    @Volatile
    private var cachedDeviceId: String? = null

    override suspend fun deviceId(): String {
        cachedDeviceId?.let { return it }
        val existing = store.data.first()[Keys.deviceId]
        if (existing != null) return existing.also { cachedDeviceId = it }
        var created = ""
        store.edit { p ->
            created = p[Keys.deviceId] ?: UUID.randomUUID().toString().also { p[Keys.deviceId] = it }
        }
        return created.also { cachedDeviceId = it }
    }

    override suspend fun setCurrency(code: String) {
        store.edit { it[Keys.currency] = code }
    }

    override suspend fun setFirstDayOfWeek(day: DayOfWeek) {
        store.edit { it[Keys.firstDayOfWeek] = day.value }
    }

    override suspend fun setMonthStartDay(day: Int) {
        store.edit { it[Keys.monthStartDay] = day.coerceIn(1, 28) }
    }

    override suspend fun setBiometricLock(enabled: Boolean) {
        store.edit { it[Keys.biometric] = enabled }
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.theme] = mode.name }
    }

    override suspend fun setLastAccountId(id: String) {
        store.edit { it[Keys.lastAccount] = id }
    }

    override suspend fun setDefaultsSeeded() {
        store.edit { it[Keys.seeded] = true }
    }

    override suspend fun updateSync(transform: (SyncSettings) -> SyncSettings) {
        store.edit { p ->
            val current = SyncSettings(
                enabled = p[Keys.syncEnabled] ?: false,
                serverUrl = p[Keys.syncUrl].orEmpty(),
                username = p[Keys.syncUser].orEmpty(),
                cursor = p[Keys.syncCursor] ?: 0L,
                lastSyncAt = p[Keys.syncLastAt] ?: 0L,
                lastError = p[Keys.syncLastError],
                e2eEnabled = p[Keys.syncE2e] ?: false,
                e2eSalt = p[Keys.syncSalt],
            )
            val next = transform(current)
            p[Keys.syncEnabled] = next.enabled
            p[Keys.syncUrl] = next.serverUrl
            p[Keys.syncUser] = next.username
            p[Keys.syncCursor] = next.cursor
            p[Keys.syncLastAt] = next.lastSyncAt
            if (next.lastError != null) p[Keys.syncLastError] = next.lastError else p.remove(Keys.syncLastError)
            p[Keys.syncE2e] = next.e2eEnabled
            if (next.e2eSalt != null) p[Keys.syncSalt] = next.e2eSalt else p.remove(Keys.syncSalt)
        }
    }
}
