package com.tgfinance.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Daily background run plus an immediate run (e.g. at app start). */
    fun scheduleRecurring() {
        workManager.enqueueUniquePeriodicWork(
            RecurringWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS).build(),
        )
        workManager.enqueueUniqueWork(
            RecurringWorker.UNIQUE_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RecurringWorker>().build(),
        )
    }
}
