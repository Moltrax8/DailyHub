package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<CategoryEntity>

    // NOCASE: "Work"/"work" spelling differences count as the same category (PK is BINARY, all access
    // paths compare NOCASE; this avoids duplicate records and preserves the visible name's spelling).
    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun getByName(name: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun delete(name: String)

    /**
     * Deletes temporary (isPermanent = 0) categories with no linked task.
     * Links are read from task_category_cross_ref. Called when a task is deleted or its tags change.
     */
    @Query(
        """
        DELETE FROM categories
        WHERE isPermanent = 0
          AND NOT EXISTS (
            SELECT 1 FROM task_category_cross_ref x
            INNER JOIN tasks t ON t.id = x.taskId
            WHERE x.categoryName = categories.name COLLATE NOCASE
          )
        """
    )
    suspend fun deleteOrphanTemporary()
}
