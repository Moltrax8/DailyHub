package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    suspend fun getById(id: String): Task?
    suspend fun upsert(task: Task)
    suspend fun delete(id: String)
    suspend fun getAll(): List<Task>
    /** ALL tasks for sync — including deleted (tombstone) records. */
    suspend fun getAllForSync(): List<Task>
    /** LWW clock of the list-order vector (for order merging). */
    suspend fun getTaskOrderTimestamp(): Long
    /** Writes the order-vector clock (now on reorder, the remote value when adopting remote). */
    suspend fun setTaskOrderTimestamp(v: Long)
    suspend fun replaceAll(tasks: List<Task>)
}
