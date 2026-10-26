package com.tgfinance.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tgfinance.app.data.settings.AppSettings
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.data.settings.ThemeMode
import com.tgfinance.app.ui.lock.AppLock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val appLock: AppLock,
) : ViewModel() {

    val uiState: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setCurrency(code: String) = launch { settings.setCurrency(code) }
    fun setFirstDayOfWeek(day: DayOfWeek) = launch { settings.setFirstDayOfWeek(day) }
    fun setMonthStartDay(day: Int) = launch { settings.setMonthStartDay(day) }
    fun setThemeMode(mode: ThemeMode) = launch { settings.setThemeMode(mode) }

    fun setBiometricLock(enabled: Boolean) = launch {
        // The user is obviously present right now; don't lock them out immediately.
        if (enabled) appLock.unlock()
        settings.setBiometricLock(enabled)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val currencies = listOf("EUR", "USD", "GBP", "CHF", "PLN", "CZK", "SEK", "NOK", "DKK", "HUF", "TRY", "JPY", "CAD", "AUD")
    }
}
