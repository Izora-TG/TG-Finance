package com.tgfinance.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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

    fun scheduleSync(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build(),
        )
    }

    /** One-off sync as soon as a network is available (app start, after local changes). */
    fun syncNow() {
        workManager.enqueueUniqueWork(
            SyncWorker.UNIQUE_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
