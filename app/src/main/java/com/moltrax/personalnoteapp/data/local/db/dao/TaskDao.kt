package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // Visible queries exclude tombstones (isDeleted = 1); sync uses the raw list.
    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<TaskEntity>

    /** ALL tasks for sync — including deleted (tombstone) records. */
    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAllRaw(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getById(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    /**
     * Soft delete (tombstone): the record is kept but marked isDeleted=1 + updatedAt is refreshed,
     * so the deletion propagates via Drive sync and is not resurrected from remote.
     */
    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    /**
     * Atomic replacement: delete + batch write in a single transaction. An interruption (kill/crash)
     * preserves the old data; otherwise the next sync would push the empty list to the Drive backup.
     */
    @Transaction
    suspend fun replaceAllAtomic(tasks: List<TaskEntity>) {
        deleteAll()
        upsertAll(tasks)
    }

    // When a category is renamed: move related tasks to the new name (also refresh updatedAt for sync).
    // NOCASE: "Work"/"work" spelling differences count as the same category.
    @Query("UPDATE tasks SET category = :newName, updatedAt = :now WHERE category = :oldName COLLATE NOCASE")
    suspend fun reassignCategory(oldName: String, newName: String, now: Long)

    // When a category is deleted: clear the category of linked tasks.
    @Query("UPDATE tasks SET category = NULL, updatedAt = :now WHERE category = :name COLLATE NOCASE")
    suspend fun clearCategory(name: String, now: Long)

    /** Bumps updatedAt so category reorderings propagate via sync (Phase 2). */
    @Query("UPDATE tasks SET updatedAt = :now WHERE id IN (:ids)")
    suspend fun touchUpdated(ids: List<String>, now: Long)
}
