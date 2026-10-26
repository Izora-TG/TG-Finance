package com.tgfinance.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tgfinance.app.data.repository.RecurringRepository
import com.tgfinance.app.util.TimeProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Books all due recurring transactions (rent, subscriptions, salary …). */
@HiltWorker
class RecurringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecurringRepository,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        repository.processDue(time.today())
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        const val UNIQUE_PERIODIC = "recurring-periodic"
        const val UNIQUE_NOW = "recurring-now"
    }
}
