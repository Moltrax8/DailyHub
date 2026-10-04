package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    /** Single-category view ordered by per-category position (Phase 2). */
    fun observeTasksForCategory(name: String): Flow<List<Task>>
    /** Multi-tag view in global manual order; matchAll=false is ANY-match. */
    fun observeTasksForCategories(names: Set<String>, matchAll: Boolean = false): Flow<List<Task>>
    suspend fun getById(id: String): Task?
    suspend fun upsert(task: Task)
    suspend fun delete(id: String)
    suspend fun getAll(): List<Task>
    /** ALL tasks for sync — including deleted (tombstone) records. */
    suspend fun getAllForSync(): List<Task>
    /** Visible tasks carrying no tag (the "untagged" bucket). */
    suspend fun getUntagged(): List<Task>
    /** Replaces the tag set (creates missing tags as temporary). */
    suspend fun setTaskCategories(taskId: String, names: Set<String>)
    /** Rewrites positions 0..n inside one category (global order untouched). Returns false when unchanged. */
    suspend fun reorderInCategory(category: String, orderedIds: List<String>): Boolean
    /** Rewrites the global manual order 0..n (single-category positions untouched). Returns false when unchanged. */
    suspend fun reorderGlobal(orderedIds: List<String>): Boolean
    /** LWW clock of the list-order vector (for order merging). */
    suspend fun getTaskOrderTimestamp(): Long
    /** Writes the order-vector clock (now on reorder, the remote value when adopting remote). */
    suspend fun setTaskOrderTimestamp(v: Long)
    suspend fun replaceAll(tasks: List<Task>)
}
