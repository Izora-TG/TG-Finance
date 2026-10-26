package com.tgfinance.app.data.repository

import com.tgfinance.app.data.local.entity.Stamp
import com.tgfinance.app.data.settings.SettingsRepository
import com.tgfinance.app.util.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/** Creates the sync metadata (timestamp + device id) for local writes. */
@Singleton
class StampFactory @Inject constructor(
    private val time: TimeProvider,
    private val settings: SettingsRepository,
) {
    suspend fun create(): Stamp = Stamp(now = time.now(), deviceId = settings.deviceId())
}
