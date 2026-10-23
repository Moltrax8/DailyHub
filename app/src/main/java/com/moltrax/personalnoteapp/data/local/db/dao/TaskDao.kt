package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    // When a category is renamed: move related tasks to the new name.
    // NOCASE: "Work"/"work" spelling differences count as the same category.
    @Query("UPDATE tasks SET category = :newName, updatedAt = :now WHERE category = :oldName COLLATE NOCASE")
    suspend fun reassignCategory(oldName: String, newName: String, now: Long)

    // When a category is deleted: clear the category of linked tasks.
    @Query("UPDATE tasks SET category = NULL, updatedAt = :now WHERE category = :name COLLATE NOCASE")
    suspend fun clearCategory(name: String, now: Long)

    @Query("UPDATE tasks SET updatedAt = :now WHERE id IN (:ids)")
    suspend fun touchUpdated(ids: List<String>, now: Long)
}
