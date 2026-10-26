package com.tgfinance.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.tgfinance.app.data.local.DefaultDataSeeder
import com.tgfinance.app.di.ApplicationScope
import com.tgfinance.app.ui.lock.AppLock
import com.tgfinance.app.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TgFinanceApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: DefaultDataSeeder
    @Inject lateinit var scheduler: WorkScheduler
    @Inject lateinit var appLock: AppLock
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLock)
        appScope.launch {
            seeder.seedIfNeeded()
            scheduler.scheduleRecurring()
        }
    }
}
