package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Project
import com.moltrax.personalnoteapp.domain.model.ProjectComment
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.ProjectStatus
import com.moltrax.personalnoteapp.domain.model.Space
import kotlinx.coroutines.flow.Flow

/**
 * Project Manager (Phase 6): containers on top of spaces(type=PROJECT).
 * Server is truth; Room holds a replace-on-fetch mirror (same as spaces).
 */
interface ProjectRepository {
    /** Creates a PROJECT space + its project row. */
    suspend fun createProject(name: String, descriptionMd: String? = null): Space

    suspend fun updateDescription(spaceId: String, descriptionMd: String?)

    /** Deletes the project row AND the underlying space (cascades content). */
    suspend fun deleteProject(spaceId: String)

    fun observeProject(spaceId: String): Flow<Project?>
    fun observeItems(spaceId: String): Flow<List<ProjectItem>>
    fun observeComments(spaceId: String, refId: String): Flow<List<ProjectComment>>

    suspend fun pullProject(spaceId: String)

    suspend fun addItem(spaceId: String, title: String, status: ProjectStatus = ProjectStatus.IDEA): ProjectItem
    suspend fun moveItem(item: ProjectItem, status: ProjectStatus)
    suspend fun editItem(itemId: String, title: String, bodyMd: String?, linkedUrl: String?)
    suspend fun deleteItem(itemId: String)

    suspend fun addComment(spaceId: String, refType: String?, refId: String?, bodyMd: String)
    suspend fun deleteComment(commentId: String)
}
