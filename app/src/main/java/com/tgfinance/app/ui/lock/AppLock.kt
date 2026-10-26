package com.tgfinance.app.ui.lock

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tgfinance.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional biometric app lock. The app locks again whenever it goes to the background
 * (observed via ProcessLifecycleOwner).
 */
@Singleton
class AppLock @Inject constructor(
    settings: SettingsRepository,
) : DefaultLifecycleObserver {

    private val unlocked = MutableStateFlow(false)

    /** True while the lock screen has to be shown. */
    val locked: Flow<Boolean> = combine(settings.settings, unlocked) { s, isUnlocked -> s.biometricLock && !isUnlocked }

    fun unlock() {
        unlocked.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        unlocked.value = false
    }
}
