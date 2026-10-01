package com.moltrax.personalnoteapp.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepo: SyncRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return when (val r = syncRepo.sync()) {
            is SyncResult.Ok -> Result.success()
            // On transient (network) errors WorkManager retries with backoff; on permanent errors it stops.
            is SyncResult.Failed -> if (r.retryable) Result.retry() else Result.failure()
        }
    }
}
