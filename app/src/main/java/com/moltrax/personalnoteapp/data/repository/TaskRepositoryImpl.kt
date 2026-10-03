package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.entity.toDomain
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val prefs: AppPreferences,
    private val scheduler: NotificationScheduler,
) : TaskRepository {

    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Task> = dao.getAll().map { it.toDomain() }

    override suspend fun getAllForSync(): List<Task> = dao.getAllRaw().map { it.toDomain() }

    override suspend fun getTaskOrderTimestamp(): Long = prefs.taskOrderUpdatedAt.first()

    override suspend fun setTaskOrderTimestamp(v: Long) {
        prefs.setTaskOrderUpdatedAt(v)
    }

    override suspend fun getById(id: String): Task? = dao.getById(id)?.toDomain()

    override suspend fun upsert(task: Task) {
        dao.upsert(task.toEntity())
        // Single choke point: every mutation stages/cancels the alarm here,
        // so writers (UI, widget, focus timer, sync) cannot forget it.
        scheduler.refresh(task)
    }

    // Soft delete (tombstone) — so sync does not resurrect the deleted task.
    override suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
        scheduler.cancel(id)
    }

    override suspend fun replaceAll(tasks: List<Task>) {
        // Orphan sweep: rows vanishing from the table (pre-tombstone deletes)
        // must not keep a staged alarm behind.
        val before = dao.getAllRaw().map { it.id }.toSet()
        dao.replaceAllAtomic(tasks.map { it.toEntity() })
        val after = tasks.map { it.id }.toSet()
        (before - after).forEach { scheduler.cancel(it) }
        tasks.forEach { scheduler.refresh(it) }
    }
}
