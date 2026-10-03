package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // Visible queries exclude tombstones (isDeleted = 1); sync uses the raw list.
    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<CategoryEntity>

    /** ALL categories for sync — including deleted (tombstone) records. */
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllRaw(): List<CategoryEntity>

    // NOCASE: "Work"/"work" spelling differences count as the same category (PK is BINARY, all access
    // paths compare NOCASE; this avoids duplicate records and preserves the visible name's spelling).
    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE")
    suspend fun getByName(name: String): CategoryEntity?

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    /**
     * Atomic replacement: delete + batch write in a single transaction (see TaskDao.replaceAllAtomic).
     */
    @Transaction
    suspend fun replaceAllAtomic(categories: List<CategoryEntity>) {
        deleteAll()
        upsertAll(categories)
    }

    /**
     * Converts temporary (isPermanent = 0) categories with no visible linked task into tombstones
     * (NOT a hard-delete — so the deletion propagates via sync and is not resurrected from remote).
     * Called when a task is deleted / its category changes.
     * Links are read from task_category_cross_ref (Phase 2); the legacy tasks.category
     * column is dual-written but no longer consulted here.
     */
    @Query(
        """
        UPDATE categories SET isDeleted = 1
        WHERE isDeleted = 0
          AND isPermanent = 0
          AND NOT EXISTS (
            SELECT 1 FROM task_category_cross_ref x
            INNER JOIN tasks t ON t.id = x.taskId
            WHERE t.isDeleted = 0
              AND x.categoryName = categories.name COLLATE NOCASE
          )
        """
    )
    suspend fun deleteOrphanTemporary()
}
