package com.tgfinance.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tgfinance.app.data.sync.SyncManager
import com.tgfinance.core.sync.SyncException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Background sync with the self-hosted server (only scheduled while sync is enabled). */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = syncManager.syncNow()
        val error = result.exceptionOrNull() as? SyncException ?: return Result.success()
        // Network and server hiccups are retried with backoff; config errors need the user.
        return when (error.kind) {
            SyncException.Kind.NETWORK, SyncException.Kind.SERVER -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC = "sync-periodic"
        const val UNIQUE_NOW = "sync-now"
    }
}
