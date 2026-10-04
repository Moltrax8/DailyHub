package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.ProjectCommentEntity
import com.moltrax.personalnoteapp.data.local.db.entity.ProjectEntity
import com.moltrax.personalnoteapp.data.local.db.entity.ProjectItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Offline cache for project content (Phase 6). Same replace-on-fetch
 * contract as [SpaceDao].
 */
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE spaceId = :spaceId")
    fun observeProject(spaceId: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM project_items WHERE spaceId = :spaceId ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeItems(spaceId: String): Flow<List<ProjectItemEntity>>

    @Query("SELECT * FROM project_comments WHERE spaceId = :spaceId ORDER BY createdAt ASC")
    fun observeComments(spaceId: String): Flow<List<ProjectCommentEntity>>

    @Transaction
    suspend fun replaceProjectCache(
        project: ProjectEntity,
        items: List<ProjectItemEntity>,
        comments: List<ProjectCommentEntity>,
    ) {
        upsertProject(project)
        deleteItems(project.spaceId)
        deleteComments(project.spaceId)
        upsertItems(items)
        upsertComments(comments)
    }

    @Upsert
    suspend fun upsertProject(project: ProjectEntity)

    @Upsert
    suspend fun upsertItems(items: List<ProjectItemEntity>)

    @Upsert
    suspend fun upsertComments(comments: List<ProjectCommentEntity>)

    @Query("DELETE FROM project_items WHERE spaceId = :spaceId")
    suspend fun deleteItems(spaceId: String)

    @Query("DELETE FROM project_comments WHERE spaceId = :spaceId")
    suspend fun deleteComments(spaceId: String)

    @Transaction
    suspend fun evictProject(spaceId: String) {
        deleteItems(spaceId)
        deleteComments(spaceId)
        deleteProject(spaceId)
    }

    @Query("DELETE FROM projects WHERE spaceId = :spaceId")
    suspend fun deleteProject(spaceId: String)
}
