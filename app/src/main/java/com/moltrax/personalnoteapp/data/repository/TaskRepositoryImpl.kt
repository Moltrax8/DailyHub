package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.entity.toDomain
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val dao: TaskDao,
    private val prefs: AppPreferences,
) : TaskRepository {

    override fun observeAll(): Flow<List<Task>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Task> = dao.getAll().map { it.toDomain() }

    override suspend fun getAllForSync(): List<Task> = dao.getAllRaw().map { it.toDomain() }

    override suspend fun getTaskOrderTimestamp(): Long = prefs.taskOrderUpdatedAt.first()

    override suspend fun setTaskOrderTimestamp(v: Long) {
        prefs.setTaskOrderUpdatedAt(v)
    }

    override suspend fun getById(id: String): Task? = dao.getById(id)?.toDomain()

    override suspend fun upsert(task: Task) = dao.upsert(task.toEntity())

    // Soft delete (tombstone) — so sync does not resurrect the deleted task.
    override suspend fun delete(id: String) = dao.softDelete(id, System.currentTimeMillis())

    override suspend fun replaceAll(tasks: List<Task>) {
        dao.replaceAllAtomic(tasks.map { it.toEntity() })
    }
}
