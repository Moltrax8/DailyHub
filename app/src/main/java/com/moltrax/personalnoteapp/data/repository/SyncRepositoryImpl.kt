package com.moltrax.personalnoteapp.data.repository

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.content.Context
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.DriveApiService
import com.moltrax.personalnoteapp.data.remote.drive.DriveAuthService
import com.moltrax.personalnoteapp.data.remote.drive.model.SyncMetadata
import com.moltrax.personalnoteapp.data.remote.drive.model.toDomain
import com.moltrax.personalnoteapp.data.remote.drive.model.toJson
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.SyncStatus
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.SyncResult
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import com.moltrax.personalnoteapp.worker.RescheduleNotificationsWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val taskRepo: TaskRepository,
    private val categoryRepo: CategoryRepository,
    private val workoutRepo: WorkoutRepository,
    private val driveApi: DriveApiService,
    private val driveAuth: DriveAuthService,
    private val prefs: AppPreferences,
    @ApplicationContext private val appContext: Context,
) : SyncRepository {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: Flow<SyncStatus> = _status

    override suspend fun sync(manual: Boolean): SyncResult = runSync(manual) { token ->
        // Pull + merge first, then push back: prevents an empty device from overwriting the remote backup
        pullInternal(token)
        pushInternal(token)
        prefs.setLastSyncAt(Instant.now().toString())
    }

    // Background push: always silent (manual = false). To avoid lost updates it does
    // pull-merge-push instead of a plain push; kept for signature compatibility.
    override suspend fun pushToDrive(): SyncResult = sync(manual = false)

    override suspend fun pullFromDrive(manual: Boolean): SyncResult = runSync(manual) { token ->
        pullInternal(token)
    }

    override fun acknowledgeStatus() {
        // Only clear the success state; keep errors visible until the user resolves them.
        if (_status.value is SyncStatus.Synced) _status.value = SyncStatus.Idle
    }

    /**
     * Common synchronization skeleton. The success state is only announced on a [manual] trigger
     * or when the previous state was ERROR (error recovery); normal background sync quietly
     * returns to Idle. Errors are always shown.
     *
     * Also returns the outcome as [SyncResult] so the caller (esp. SyncWorker) can decide
     * success/retry/failure. Coroutine cancellation ([CancellationException]) is never swallowed.
     */
    private suspend fun runSync(manual: Boolean, block: suspend (token: String) -> Unit): SyncResult {
        // Skip silently when not signed in (for the automatic sync at startup).
        if (driveAuth.getLastSignedInAccount() == null) return SyncResult.Ok
        val announce = manual || _status.value is SyncStatus.Error
        if (announce) _status.value = SyncStatus.Syncing
        return try {
            val token = driveAuth.getFreshToken()
                ?: throw IllegalStateException("Erişim jetonu alınamadı (oturum geçersiz olabilir)")
            block(token)
            _status.value = if (announce) SyncStatus.Synced else SyncStatus.Idle
            SyncResult.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _status.value = SyncStatus.Error(e.detail())
            SyncResult.Failed(e, e.isRetryable())
        }
    }

    /** Transient (network) errors are retryable; auth/parse/programming errors are permanent. */
    private fun Throwable.isRetryable(): Boolean =
        this is IOException || cause is IOException

    // Builds the full error detail: exception type + message + root cause (if any).
    private fun Throwable.detail(): String = buildString {
        append(this@detail::class.java.simpleName)
        message?.let { append(": "); append(it) }
        cause?.let { c ->
            append(" | neden: ").append(c::class.java.simpleName)
            c.message?.let { append(": "); append(it) }
        }
    }

    private suspend fun pushInternal(token: String) {
        val meta = buildMetadata()
        // On the first push from a new device, discover the existing remote file
        val fileId = prefs.driveFileId.first() ?: driveApi.findOrNull(token)?.id

        val result = driveApi.upload(token, meta, fileId)
        prefs.setDriveFileId(result.fileId)
    }

    private suspend fun pullInternal(token: String) {
        var fileId = prefs.driveFileId.first() ?: driveApi.findOrNull(token)?.id ?: return
        var remote = driveApi.download(token, fileId)
        if (remote == null) {
            // The cached fileId may be stale (file deleted / account changed): clear the cache
            // and rediscover; if still missing there is no backup to pull (return silently).
            prefs.setDriveFileId(null)
            fileId = driveApi.findOrNull(token)?.id ?: return
            remote = driveApi.download(token, fileId) ?: return
        }

        // Tasks — LWW by updatedAt (tombstones arrive in the raw list; newer updatedAt wins,
        // so deletions propagate and are not resurrected from remote). Ties resolve deterministically.
        val localTasks = taskRepo.getAllForSync()
        val mergedTasks = mergeById(localTasks, remote.tasks.map { it.toDomain() }, { it.id }) { l, r ->
            if (r.updatedAt > l.updatedAt) r else pickOnTie(l, r)
        }
        // List-order vector: adopt the peer's vector when it is newer (without touching row
        // timestamps — order info travels in the vector and does not pollute row LWW); otherwise
        // publish the local order. Concurrent reorderings no longer produce a chimeric mix.
        val localOrderTs = taskRepo.getTaskOrderTimestamp()
        val orderedTasks = if (remote.taskOrderUpdatedAt > localOrderTs && remote.taskOrder.isNotEmpty()) {
            taskRepo.setTaskOrderTimestamp(remote.taskOrderUpdatedAt)
            applyRemoteOrder(mergedTasks, remote.taskOrder)
        } else mergedTasks
        taskRepo.replaceAll(orderedTasks)
        // If the pulled merge changed anything alarm-relevant (completion/deletion/due date),
        // refresh staged alarms. The widget also updates itself via the DB observer.
        rescheduleAlarmsIfNeeded(localTasks, orderedTasks)

        // Categories — merge by name; tombstones are contagious (deleted on either side stays
        // deleted), permanence is kept if either side is permanent.
        val mergedCategories = mergeById(
            categoryRepo.getAllForSync(), remote.categories.map { it.toDomain() }, { it.name },
        ) { l, r ->
            if (l.isDeleted || r.isDeleted) {
                Category(name = l.name, isPermanent = l.isPermanent || r.isPermanent, isDeleted = true)
            } else if (l.isPermanent || r.isPermanent) l.copy(isPermanent = true) else l
        }
        categoryRepo.replaceAll(mergedCategories)
        // After merging, clean up temporary categories left with no linked tasks
        categoryRepo.cleanupTemporary()

        // Workout groups — LWW by group updatedAt. Tombstones (isDeleted) are included,
        // so a group/workout deleted on one side is NOT resurrected from the other side.
        val mergedGroups = mergeById(workoutRepo.getGroupsForSync(), remote.workoutGroups, { it.id }) { l, r ->
            if (r.updatedAt > l.updatedAt) r else pickOnTie(l, r)
        }
        workoutRepo.replaceGroups(mergedGroups)

        // Workout sessions — immutable records merge by id; tombstones are contagious
        // (deleted on either side stays deleted), local fields win on conflict.
        val mergedSessions = mergeById(workoutRepo.getSessionsForSync(), remote.workoutSessions, { it.id }) { l, r ->
            if (l.isDeleted || r.isDeleted) l.copy(isDeleted = true) else l
        }
        workoutRepo.replaceSessions(mergedSessions)
    }

    private suspend fun buildMetadata(): SyncMetadata {
        // Including tombstones — so other devices learn about deletions.
        val tasks = taskRepo.getAllForSync()
        return SyncMetadata(
            lastModifiedUtc = Instant.now().toString(),
            tasks           = tasks.map { it.toJson() },
            categories      = categoryRepo.getAllForSync().map { it.toJson() },
            workoutGroups   = workoutRepo.getGroupsForSync(),
            workoutSessions = workoutRepo.getSessionsForSync(),
            taskOrder = tasks.sortedBy { it.sortOrder }.map { it.id },
            taskOrderUpdatedAt = taskRepo.getTaskOrderTimestamp(),
        )
    }

    /**
     * Applies the remote order vector to the locally merged list. Unknown ids (absent on the
     * peer) stay at the end of the list in their current relative order. sortOrder is rewritten
     * but updatedAt is NOT touched — since order info travels in the vector, row LWW clocks stay clean.
     */
    private fun applyRemoteOrder(tasks: List<Task>, order: List<String>): List<Task> {
        val rank = order.withIndex().associate { it.value to it.index }
        return tasks
            .sortedWith(compareBy({ rank[it.id] ?: Int.MAX_VALUE }, { it.sortOrder }))
            .mapIndexed { index, t ->
                if (t.sortOrder != index.toLong()) t.copy(sortOrder = index.toLong()) else t
            }
    }

    /** Alarm signature: id → (is done, due date, is deleted). Order changes are not in the signature. */
    private fun alarmSignature(tasks: List<Task>): Map<String, Triple<Boolean, Long?, Boolean>> =
        tasks.associate { it.id to Triple(it.isDone, it.dueDate, it.isDeleted) }

    /**
     * If the merge changed anything alarm-relevant, refresh staged alarms with a deduplicated
     * reschedule job (so no ghost/missing notifications remain). If nothing changed, do not
     * wake the alarm system (battery/noise).
     */
    private fun rescheduleAlarmsIfNeeded(before: List<Task>, after: List<Task>) {
        if (alarmSignature(before) == alarmSignature(after)) return
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            RescheduleNotificationsWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build(),
        )
    }

    /**
     * Picks a deterministic winner on an LWW tie (same id + same updatedAt): both devices
     * compute the same result from the same inputs, so the merge converges. Lexicographic order
     * is arbitrary but stable (it does not replace the timestamp, it only breaks ties).
     */
    private fun <T : Any> pickOnTie(local: T, remote: T): T =
        if (remote.toString() >= local.toString()) remote else local

    /** Generic id-keyed merge; [resolve] picks the winner when an id exists on both sides. */
    private fun <T : Any> mergeById(
        local: List<T>,
        remote: List<T>,
        id: (T) -> String,
        resolve: (local: T, remote: T) -> T,
    ): List<T> {
        val map = LinkedHashMap<String, T>()
        local.forEach { map[id(it)] = it }
        remote.forEach { r ->
            val key = id(r)
            val existing = map[key]
            map[key] = if (existing == null) r else resolve(existing, r)
        }
        return map.values.toList()
    }
}
