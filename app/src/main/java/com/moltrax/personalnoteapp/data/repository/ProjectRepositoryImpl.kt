package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.ProjectDao
import com.moltrax.personalnoteapp.data.local.db.dao.SpaceDao
import com.moltrax.personalnoteapp.data.local.db.entity.ProjectCommentEntity
import com.moltrax.personalnoteapp.data.local.db.entity.ProjectEntity
import com.moltrax.personalnoteapp.data.local.db.entity.ProjectItemEntity
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.domain.model.Project
import com.moltrax.personalnoteapp.domain.model.ProjectComment
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.ProjectStatus
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceType
import com.moltrax.personalnoteapp.domain.model.projectStatusOf
import com.moltrax.personalnoteapp.domain.repository.ProjectRepository
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val db: SupabaseDbApi?,
    private val auth: SupabaseAuthService,
    private val cache: ProjectDao,
    private val spaces: SpaceRepository,
) : ProjectRepository {

    private fun api(): SupabaseDbApi =
        db ?: throw IllegalStateException("Supabase is not configured.")

    private suspend fun bearer(): String {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        return "Bearer $token"
    }

    private suspend fun myId(): String =
        auth.currentUserId() ?: throw IllegalStateException("Not signed in.")

    private fun <T> checked(res: retrofit2.Response<T>, what: String): T {
        if (!res.isSuccessful) throw IOException("$what (HTTP ${res.code()}).")
        return res.body() ?: throw IOException("$what (empty response).")
    }

    private fun Project.toEntity() = ProjectEntity(spaceId, descriptionMd, boardColumns)
    private fun ProjectEntity.toDomain() = Project(spaceId, descriptionMd, boardColumns)
    private fun ProjectItem.toEntity() = ProjectItemEntity(
        id, spaceId, title, status.name.lowercase().replaceFirstChar { it.uppercase() },
        bodyMd, linkedUrl, sortOrder, updatedAt,
    )
    private fun ProjectItemEntity.toDomain() = ProjectItem(
        id, spaceId, title, projectStatusOf(status), bodyMd, linkedUrl, sortOrder, updatedAt,
    )
    private fun ProjectComment.toEntity() = ProjectCommentEntity(id, spaceId, refType, refId, author, bodyMd, createdAt)
    private fun ProjectCommentEntity.toDomain() = ProjectComment(id, spaceId, refType, refId, author, bodyMd, createdAt)

    override suspend fun createProject(name: String, descriptionMd: String?): Space {
        val me = myId()
        val token = bearer()
        val spaceId = UUID.randomUUID().toString()
        checked(
            api().createSpace(
                token,
                body = buildJsonObject {
                    put("id", spaceId)
                    put("type", "PROJECT")
                    put("name", name.trim())
                    put("created_by", me)
                },
            ),
            "Project space create failed",
        )
        checked(api().addMember(token, body = mapOf("space_id" to spaceId, "user_id" to me, "role" to "owner")), "Join failed")
        checked(
            api().upsertProject(
                token,
                body = buildJsonObject {
                    put("space_id", spaceId)
                    put("description_md", descriptionMd?.takeIf { it.isNotBlank() })
                    putJsonArray("board_columns") {
                        listOf("Idea", "Planned", "Developing", "Finished").forEach { add(JsonPrimitive(it)) }
                    }
                },
            ),
            "Project row create failed",
        )
        pullProject(spaceId)
        return Space(spaceId, SpaceType.PROJECT, name.trim(), me, "", "")
    }

    override suspend fun updateDescription(spaceId: String, descriptionMd: String?) {
        // Description lives on the projects row: upsert keyed by space_id.
        // JsonNull (not a dropped key) so clearing the description works.
        val clean = descriptionMd?.trim()?.takeIf { it.isNotBlank() }
        val res = api().upsertProject(
            bearer(),
            body = buildJsonObject {
                put("space_id", spaceId)
                put("description_md", clean?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
            },
        )
        if (!res.isSuccessful) throw IOException("Description update failed (HTTP ${res.code()}).")
        pullProject(spaceId)
    }

    override suspend fun deleteProject(spaceId: String) {
        // Deleting the space cascades everything server-side (incl. the project row).
        spaces.deleteSpace(spaceId)
        cache.evictProject(spaceId)
    }

    override fun observeProject(spaceId: String): Flow<Project?> =
        cache.observeProject(spaceId).map { it?.toDomain() }

    override fun observeItems(spaceId: String): Flow<List<ProjectItem>> =
        cache.observeItems(spaceId).map { list -> list.map { it.toDomain() } }

    override fun observeComments(spaceId: String, refId: String): Flow<List<ProjectComment>> =
        cache.observeComments(spaceId).map { list -> list.filter { it.refId == refId }.map { it.toDomain() } }

    override suspend fun pullProject(spaceId: String) {
        val token = bearer()
        val project = checked(api().getProject(token, "eq.$spaceId"), "Project load failed").firstOrNull()
        val items = checked(api().projectItems(token, "eq.$spaceId"), "Items load failed")
        val comments = items.flatMap { item ->
            checked(api().itemComments(token, "eq.$spaceId", "eq.${item.id}"), "Comments load failed")
        }
        if (project != null) {
            cache.replaceProjectCache(
                project.toEntity(),
                items.map { it.toEntity() },
                comments.map { it.toEntity() },
            )
        }
    }

    override suspend fun addItem(spaceId: String, title: String, status: ProjectStatus): ProjectItem {
        val clean = title.trim()
        require(clean.isNotBlank()) { "Title required." }
        val id = UUID.randomUUID().toString()
        checked(
            api().createProjectItem(
                bearer(),
                body = buildJsonObject {
                    put("id", id)
                    put("space_id", spaceId)
                    put("title", clean)
                    put("status", status.name.lowercase().replaceFirstChar { it.uppercase() })
                    put("sort_order", 0L)
                },
            ),
            "Item create failed",
        )
        appendFeed(spaceId, "item.added", id)
        pullProject(spaceId)
        return ProjectItem(id, spaceId, clean, status, sortOrder = 0L)
    }

    override suspend fun moveItem(item: ProjectItem, status: ProjectStatus) {
        val res = api().updateProjectItem(
            bearer(), "eq.${item.id}",
            body = buildJsonObject {
                put("status", status.name.lowercase().replaceFirstChar { it.uppercase() })
            },
        )
        if (!res.isSuccessful) throw IOException("Move failed (HTTP ${res.code()}).")
        pullProject(item.spaceId)
    }

    override suspend fun editItem(itemId: String, title: String, bodyMd: String?, linkedUrl: String?) {
        val clean = title.trim()
        require(clean.isNotBlank()) { "Title required." }
        val url = linkedUrl?.trim()?.takeIf { it.isNotBlank() }
        if (url != null) require(url.startsWith("http://") || url.startsWith("https://")) { "Link must start with http(s)://." }
        val res = api().updateProjectItem(
            bearer(), "eq.$itemId",
            body = buildJsonObject {
                put("title", clean)
                put("body_md", bodyMd?.takeIf { it.isNotBlank() })
                put("linked_url", url)
            },
        )
        if (!res.isSuccessful) throw IOException("Item update failed (HTTP ${res.code()}).")
    }

    override suspend fun deleteItem(itemId: String) {
        val res = api().deleteProjectItem(bearer(), "eq.$itemId")
        if (!res.isSuccessful) throw IOException("Item delete failed (HTTP ${res.code()}).")
    }

    override suspend fun addComment(spaceId: String, refType: String?, refId: String?, bodyMd: String) {
        val clean = bodyMd.trim()
        require(clean.isNotBlank()) { "Comment required." }
        val id = UUID.randomUUID().toString()
        checked(
            api().createComment(
                bearer(),
                body = buildJsonObject {
                    put("id", id)
                    put("space_id", spaceId)
                    put("ref_type", refType)
                    put("ref_id", refId)
                    put("author", myId())
                    put("body_md", clean)
                },
            ),
            "Comment create failed",
        )
        appendFeed(spaceId, "comment.added", id)
        pullProject(spaceId)
    }

    override suspend fun deleteComment(commentId: String) {
        val res = api().deleteComment(bearer(), "eq.$commentId")
        if (!res.isSuccessful) throw IOException("Comment delete failed (HTTP ${res.code()}).")
    }

    /** Best-effort feed writer: failures are swallowed so features never break. */
    private suspend fun appendFeed(spaceId: String, kind: String, ref: String?) {
        runCatching {
            api().appendFeed(
                bearer(),
                body = buildJsonObject {
                    put("space_id", spaceId)
                    put("kind", kind)
                    if (ref != null) {
                        put("ref", buildJsonObject { put("id", ref) })
                    }
                    put("actor", myId())
                },
            )
        }
    }
}
