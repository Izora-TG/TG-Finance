package com.tgfinance.app.fakes

import com.tgfinance.app.data.settings.AppSettings
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.data.settings.SyncSettings
import com.tgfinance.app.data.settings.ThemeMode
import com.tgfinance.app.util.IdGenerator
import com.tgfinance.app.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeTimeProvider(
    var today: LocalDate = LocalDate.of(2024, 5, 15),
    var now: Long = 1_715_760_000_000L,
) : TimeProvider {
    override fun now(): Long = now++
    override fun today(): LocalDate = today
}

class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var next = 1
    override fun newId(): String = "$prefix-${next++}"
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(defaultsSeeded = true),
    private val deviceId: String = "test-device",
) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val sync = MutableStateFlow(SyncSettings())

    override val settings = state
    override val syncSettings = sync
    override suspend fun deviceId(): String = deviceId
    override suspend fun setCurrency(code: String) = state.update { it.copy(currencyCode = code) }
    override suspend fun setFirstDayOfWeek(day: DayOfWeek) = state.update { it.copy(firstDayOfWeek = day) }
    override suspend fun setMonthStartDay(day: Int) = state.update { it.copy(monthStartDay = day) }
    override suspend fun setBiometricLock(enabled: Boolean) = state.update { it.copy(biometricLock = enabled) }
    override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }
    override suspend fun setLastAccountId(id: String) = state.update { it.copy(lastAccountId = id) }
    override suspend fun setDefaultsSeeded() = state.update { it.copy(defaultsSeeded = true) }
    override suspend fun updateSync(transform: (SyncSettings) -> SyncSettings) = sync.update(transform)
}

class FakeSyncController : com.tgfinance.app.data.sync.SyncController {
    val state = MutableStateFlow(com.tgfinance.app.data.sync.SyncStatus())
    var syncCalls = 0
    override val status = state
    override suspend fun syncNow(): Result<com.tgfinance.core.sync.SyncReport?> {
        syncCalls++
        return Result.success(null)
    }
}
