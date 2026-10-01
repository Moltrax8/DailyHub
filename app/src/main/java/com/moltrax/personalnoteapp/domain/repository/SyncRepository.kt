package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Machine-readable outcome of a sync operation (the human-readable state stays in the
 * [SyncStatus] flow). Since it is a return type, callers may ignore it;
 * [androidx.work.CoroutineWorker] uses it for the success/retry/failure decision.
 */
sealed interface SyncResult {
    data object Ok : SyncResult

    /**
     * [retryable] true = transient error (network) → worker should retry;
     * false = permanent error (auth/parse) → worker should report failure.
     */
    data class Failed(val error: Throwable, val retryable: Boolean) : SyncResult
}

interface SyncRepository {
    val syncStatus: Flow<SyncStatus>

    /**
     * Background (automatic) push — called as data changes, its success stays silent.
     * Internally does pull-merge-push to avoid lost updates (a plain push could overwrite
     * concurrently edited data); kept for signature compatibility.
     */
    suspend fun pushToDrive(): SyncResult

    suspend fun pullFromDrive(manual: Boolean = false): SyncResult

    /**
     * Safe full sync: first pulls remote data and merges it locally, then pushes the merged
     * result back. Prevents a new/empty device from overwriting the remote backup with empty
     * data. Used at startup and for manual "Sync now".
     *
     * When [manual] is true (user-triggered) the success state is shown in the UI.
     * For automatic (background) sync, success is only shown when the previous state was ERROR;
     * otherwise it completes silently.
     */
    suspend fun sync(manual: Boolean = false): SyncResult

    /** Clears the shown "Synced" success state (pulls back to Idle). Does not clear errors. */
    fun acknowledgeStatus()
}
