package com.tgfinance.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.data.settings.ThemeMode
import com.tgfinance.app.ui.lock.AppLock
import com.tgfinance.app.ui.lock.LockScreen
import com.tgfinance.app.ui.navigation.AppScaffold
import com.tgfinance.app.ui.navigation.toAdaptiveInfo
import com.tgfinance.app.ui.theme.TgFinanceTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** FragmentActivity is required by [BiometricPrompt]. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var appLock: AppLock

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val locked by appLock.locked.collectAsStateWithLifecycle(initialValue = true)
            val windowSize = calculateWindowSizeClass(this)

            val lockEnabled = settings?.biometricLock == true
            LaunchedEffect(lockEnabled) {
                // Hide content from the recents screen and screenshots when the lock is on.
                if (lockEnabled) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            TgFinanceTheme(
                themeMode = settings?.themeMode ?: ThemeMode.DARK,
                currencyCode = settings?.currencyCode ?: "EUR",
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (settings != null) {
                        Box(Modifier.fillMaxSize()) {
                            // The app stays composed under the lock so navigation state survives.
                            AppScaffold(adaptiveInfo = windowSize.toAdaptiveInfo())
                            if (locked) LockScreen(onUnlock = ::authenticate)
                        }
                    }
                }
            }
        }
    }

    private fun authenticate() {
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // Nothing enrolled (or no hardware): never lock the user out of their own data.
            appLock.unlock()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    appLock.unlock()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_prompt_title))
                .setSubtitle(getString(R.string.lock_prompt_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
