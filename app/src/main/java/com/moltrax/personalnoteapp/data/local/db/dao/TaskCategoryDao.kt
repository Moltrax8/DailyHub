package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.TaskCategoryCrossRef
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Per-category task positions (Phase 2, Room v20).
 *
 * Name matching is NOCASE in SQL; multi-tag set logic (ANY/ALL match) lives in
 * the repository in Kotlin so casing behaves identically everywhere. Flows reuse
 * [TaskDao.observeAll] + [observeAllLinks] (personal-scale data, zero new
 * ordering queries to keep divergent).
 */
@Dao
interface TaskCategoryDao {

    @Query("SELECT * FROM task_category_cross_ref")
    suspend fun getAllLinks(): List<TaskCategoryCrossRef>

    @Query("SELECT * FROM task_category_cross_ref")
    fun observeAllLinks(): Flow<List<TaskCategoryCrossRef>>

    @Query("SELECT * FROM task_category_cross_ref WHERE taskId = :taskId")
    suspend fun getLinksForTask(taskId: String): List<TaskCategoryCrossRef>

    @Upsert
    suspend fun upsertLinks(links: List<TaskCategoryCrossRef>)

    @Query("DELETE FROM task_category_cross_ref WHERE taskId = :taskId")
    suspend fun deleteLinksForTask(taskId: String)

    @Query("DELETE FROM task_category_cross_ref WHERE categoryName = :name COLLATE NOCASE")
    suspend fun clearCategoryLinks(name: String)

    @Query(
        "SELECT COALESCE(MAX(sortOrder), -1) FROM task_category_cross_ref " +
            "WHERE categoryName = :name COLLATE NOCASE"
    )
    suspend fun maxSortForCategory(name: String): Long

    @Query(
        "UPDATE task_category_cross_ref SET sortOrder = :order " +
            "WHERE taskId = :taskId AND categoryName = :name COLLATE NOCASE"
    )
    suspend fun setLinkOrder(taskId: String, name: String, order: Long)

    /** Rewrites positions 0..n for the category in one transaction. */
    @Transaction
    suspend fun reorderInCategory(name: String, orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> setLinkOrder(id, name, index.toLong()) }
    }

    /**
     * Renames a tag on all links. Links that would collide with an existing
     * same-task link under the new name are absorbed (deleted first).
     */
    @Transaction
    suspend fun renameCategoryLinks(oldName: String, newName: String) {
        deleteRenameConflicts(oldName, newName)
        updateLinkName(oldName, newName)
    }

    @Query(
        "DELETE FROM task_category_cross_ref WHERE categoryName = :oldName COLLATE NOCASE " +
            "AND taskId IN (SELECT taskId FROM task_category_cross_ref WHERE categoryName = :newName COLLATE NOCASE)"
    )
    suspend fun deleteRenameConflicts(oldName: String, newName: String)

    @Query(
        "UPDATE task_category_cross_ref SET categoryName = :newName " +
            "WHERE categoryName = :oldName COLLATE NOCASE"
    )
    suspend fun updateLinkName(oldName: String, newName: String)

    /** Visible tasks carrying no tag (the "untagged" bucket). */
    @Query(
        """
        SELECT t.* FROM tasks t WHERE t.isDeleted = 0
        AND NOT EXISTS (SELECT 1 FROM task_category_cross_ref x WHERE x.taskId = t.id)
        ORDER BY t.sortOrder ASC, t.createdAt DESC
        """
    )
    suspend fun getUntagged(): List<TaskEntity>
}
