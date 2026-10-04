package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.SpaceDao
import com.moltrax.personalnoteapp.data.local.db.entity.SharedNoteEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SharedTaskEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceLinkEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceMemberEntity
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
import com.moltrax.personalnoteapp.domain.model.SpaceType
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceRepositoryImpl @Inject constructor(
    private val db: SupabaseDbApi?,
    private val auth: SupabaseAuthService,
    private val cache: SpaceDao,
) : SpaceRepository {

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

    // -- cache mapping -------------------------------------------------------

    private fun Space.toEntity() = SpaceEntity(
        id = id, type = type.name, name = name, createdBy = createdBy,
        createdAt = createdAt, updatedAt = updatedAt.ifBlank { createdAt },
    )

    private fun SpaceEntity.toDomain() = Space(
        id = id, type = runCatching { SpaceType.valueOf(type) }.getOrDefault(SpaceType.DUO),
        name = name, createdBy = createdBy, createdAt = createdAt, updatedAt = updatedAt,
    )

    private fun SpaceMember.toEntity(spaceId: String) = SpaceMemberEntity(spaceId, userId, role, joinedAt)
    private fun SpaceMemberEntity.toDomain() = SpaceMember(spaceId, userId, role, joinedAt)
    private fun SharedNote.toEntity() = SharedNoteEntity(id, spaceId, author, title, bodyMd, updatedAt)
    private fun SharedNoteEntity.toDomain() = SharedNote(id, spaceId, author, title, bodyMd, updatedAt)
    private fun SharedTask.toEntity() = SharedTaskEntity(id, spaceId, title, isDone, assignee, dueAt, sortOrder, updatedAt)
    private fun SharedTaskEntity.toDomain() = SharedTask(id, spaceId, title, isDone, assignee, dueAt, sortOrder, updatedAt)
    private fun SpaceLink.toEntity() = SpaceLinkEntity(id, spaceId, url, title, createdBy, createdAt)
    private fun SpaceLinkEntity.toDomain() = SpaceLink(id, spaceId, url, title, createdBy, createdAt)

    // -- spaces --------------------------------------------------------------

    override fun observeSpaces(): Flow<List<Space>> =
        cache.observeSpaces().map { list -> list.map { it.toDomain() } }

    override suspend fun pullSpace(spaceId: String) {
        val token = bearer()
        val spaces = checked(api().mySpaces(token), "Spaces load failed")
        cache.upsertSpaces(spaces.map { it.toEntity() })
        val space = spaces.firstOrNull { it.id == spaceId } ?: cache.getSpace(spaceId)?.toDomain()
        if (space != null) {
            val members = checked(api().spaceMembers(token, "eq.$spaceId"), "Members load failed")
            val notes = checked(api().spaceNotes(token, "eq.$spaceId"), "Notes load failed")
            val tasks = checked(api().spaceTasks(token, "eq.$spaceId"), "Tasks load failed")
            val links = checked(api().spaceLinks(token, "eq.$spaceId"), "Links load failed")
            cache.replaceSpaceCache(
                space.toEntity(),
                members.map { it.toEntity(spaceId) },
                notes.map { it.toEntity() },
                tasks.map { it.toEntity() },
                links.map { it.toEntity() },
            )
        }
    }

    override suspend fun createDuo(friendUserId: String, name: String?): Space {
        val me = myId()
        require(friendUserId != me) { "Cannot open a Duo hub with yourself." }
        val token = bearer()
        // Explicit create (accepted friendship is the invite — no dangling invites).
        val space = checked(
            api().createSpace(token, body = mapOf("type" to "DUO", "name" to name, "created_by" to me)),
            "Duo hub create failed",
        ).first()
        checked(api().addMember(token, body = mapOf("space_id" to space.id, "user_id" to me, "role" to "owner")), "Join failed")
        checked(
            api().addMember(token, body = mapOf("space_id" to space.id, "user_id" to friendUserId, "role" to "member")),
            "Invite failed",
        )
        cache.upsertSpaces(listOf(space.toEntity()))
        return space
    }

    override suspend fun renameSpace(spaceId: String, name: String?) {
        val res = api().renameSpace(bearer(), "eq.$spaceId", mapOf("name" to name))
        if (!res.isSuccessful) throw IOException("Rename failed (HTTP ${res.code()}).")
        pullSpace(spaceId)
    }

    override suspend fun deleteSpace(spaceId: String) {
        val res = api().deleteSpace(bearer(), "eq.$spaceId")
        if (!res.isSuccessful) throw IOException("Delete failed (HTTP ${res.code()}).")
        cache.evictSpace(spaceId)
    }

    override suspend fun inviteMember(spaceId: String, userId: String, asOwner: Boolean) {
        checked(
            api().addMember(
                bearer(),
                body = mapOf(
                    "space_id" to spaceId, "user_id" to userId,
                    "role" to if (asOwner) "owner" else "member",
                ),
            ),
            "Invite failed",
        )
        pullSpace(spaceId)
    }

    override suspend fun removeMember(spaceId: String, userId: String) {
        val res = api().removeMember(bearer(), "eq.$spaceId", "eq.$userId")
        if (!res.isSuccessful) throw IOException("Remove failed (HTTP ${res.code()}).")
        pullSpace(spaceId)
    }

    override suspend fun leaveSpace(spaceId: String) {
        removeMember(spaceId, myId())
        cache.evictSpace(spaceId)
    }

    // -- cached content ------------------------------------------------------

    override fun observeMembers(spaceId: String): Flow<List<SpaceMember>> =
        cache.observeMembers(spaceId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMembers(spaceId: String): List<SpaceMember> {
        pullSpace(spaceId)
        return observeMembers(spaceId).first()
    }

    override fun observeNotes(spaceId: String): Flow<List<SharedNote>> =
        cache.observeNotes(spaceId).map { list -> list.map { it.toDomain() } }

    override fun observeTasks(spaceId: String): Flow<List<SharedTask>> =
        cache.observeSharedTasks(spaceId).map { list -> list.map { it.toDomain() } }

    override fun observeLinks(spaceId: String): Flow<List<SpaceLink>> =
        cache.observeLinks(spaceId).map { list -> list.map { it.toDomain() } }

    override suspend fun addNote(spaceId: String, title: String?, bodyMd: String?): SharedNote {
        val me = myId()
        val note = checked(
            api().createNote(
                bearer(),
                body = mapOf("space_id" to spaceId, "author" to me, "title" to title, "body_md" to bodyMd),
            ),
            "Note create failed",
        ).first()
        pullSpace(spaceId)
        return note
    }

    override suspend fun editNote(noteId: String, title: String?, bodyMd: String?) {
        val res = api().updateNote(bearer(), "eq.$noteId", mapOf("title" to title, "body_md" to bodyMd))
        if (!res.isSuccessful) throw IOException("Note update failed (HTTP ${res.code()}).")
    }

    override suspend fun deleteNote(noteId: String) {
        val res = api().deleteNote(bearer(), "eq.$noteId")
        if (!res.isSuccessful) throw IOException("Note delete failed (HTTP ${res.code()}).")
    }

    override suspend fun addSharedTask(spaceId: String, title: String, assignee: String?): SharedTask {
        val body = buildJsonObject {
            put("space_id", spaceId)
            put("title", title)
            put("assignee", assignee)
            put("sort_order", 0L)
        }
        val task = checked(api().createSharedTask(bearer(), body = body), "Task create failed").first()
        pullSpace(spaceId)
        return task
    }

    override suspend fun toggleSharedTask(task: SharedTask) {
        val body = buildJsonObject { put("is_done", !task.isDone) }
        val res = api().updateSharedTask(bearer(), "eq.${task.id}", body)
        if (!res.isSuccessful) throw IOException("Task update failed (HTTP ${res.code()}).")
    }

    override suspend fun deleteSharedTask(taskId: String) {
        val res = api().deleteSharedTask(bearer(), "eq.$taskId")
        if (!res.isSuccessful) throw IOException("Task delete failed (HTTP ${res.code()}).")
    }

    override suspend fun addLink(spaceId: String, url: String, title: String?): SpaceLink {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "Link must start with http(s)://." }
        val link = checked(
            api().createLink(
                bearer(),
                body = mapOf("space_id" to spaceId, "url" to trimmed, "title" to title?.takeIf { it.isNotBlank() }),
            ),
            "Link create failed",
        ).first()
        pullSpace(spaceId)
        return link
    }

    override suspend fun deleteLink(linkId: String) {
        val res = api().deleteLink(bearer(), "eq.$linkId")
        if (!res.isSuccessful) throw IOException("Link delete failed (HTTP ${res.code()}).")
    }
}
