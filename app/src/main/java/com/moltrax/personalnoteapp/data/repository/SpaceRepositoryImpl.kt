package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.SpaceDao
import com.moltrax.personalnoteapp.data.local.db.entity.SharedNoteEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SharedTaskEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceLinkEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceMemberEntity
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseDbApi
import com.moltrax.personalnoteapp.di.SupabaseConfig
import com.moltrax.personalnoteapp.domain.model.FeedEntry
import com.moltrax.personalnoteapp.domain.model.FeedKind
import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceEvent
import com.moltrax.personalnoteapp.domain.model.SpaceFile
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
import com.moltrax.personalnoteapp.domain.model.SpaceMessage
import com.moltrax.personalnoteapp.domain.model.SpaceType
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpaceRepositoryImpl @Inject constructor(
    private val db: SupabaseDbApi?,
    private val auth: SupabaseAuthService,
    private val cache: SpaceDao,
    private val config: SupabaseConfig,
    private val http: OkHttpClient,
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
        // Client-generated id: creates use return=minimal (representation
        // re-runs SELECT RLS, which fails on member-gated rows).
        val spaceId = UUID.randomUUID().toString()
        // Explicit create (accepted friendship is the invite — no dangling invites).
        checked(
            api().createSpace(
                token,
                body = buildJsonObject {
                    put("id", spaceId)
                    put("type", "DUO")
                    put("name", name)
                    put("created_by", me)
                },
            ),
            "Duo hub create failed",
        )
        checked(api().addMember(token, body = mapOf("space_id" to spaceId, "user_id" to me, "role" to "owner")), "Join failed")
        checked(
            api().addMember(token, body = mapOf("space_id" to spaceId, "user_id" to friendUserId, "role" to "member")),
            "Invite failed",
        )
        pullSpace(spaceId)
        return cache.getSpace(spaceId)?.toDomain()
            ?: Space(spaceId, SpaceType.DUO, name, me, "", "")
    }

    override suspend fun renameSpace(spaceId: String, name: String?) {
        val res = api().renameSpace(
            bearer(), "eq.$spaceId",
            body = buildJsonObject { put("name", name) },
        )
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
        appendFeed(spaceId, FeedKind.MEMBER_JOINED, userId)
        pullSpace(spaceId)
    }

    override suspend fun removeMember(spaceId: String, userId: String) {
        val res = api().removeMember(bearer(), "eq.$spaceId", "eq.$userId")
        if (!res.isSuccessful) throw IOException("Remove failed (HTTP ${res.code()}).")
        appendFeed(spaceId, FeedKind.MEMBER_LEFT, userId)
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
        val id = UUID.randomUUID().toString()
        checked(
            api().createNote(
                bearer(),
                body = buildJsonObject {
                    put("id", id)
                    put("space_id", spaceId)
                    put("author", me)
                    put("title", title)
                    put("body_md", bodyMd)
                },
            ),
            "Note create failed",
        )
        appendFeed(spaceId, FeedKind.NOTE_ADDED, id)
        pullSpace(spaceId)
        return SharedNote(id, spaceId, me, title, bodyMd)
    }

    override suspend fun editNote(noteId: String, title: String?, bodyMd: String?) {
        val res = api().updateNote(
            bearer(), "eq.$noteId",
            body = buildJsonObject {
                put("title", title)
                put("body_md", bodyMd)
            },
        )
        if (!res.isSuccessful) throw IOException("Note update failed (HTTP ${res.code()}).")
    }

    override suspend fun deleteNote(noteId: String) {
        val res = api().deleteNote(bearer(), "eq.$noteId")
        if (!res.isSuccessful) throw IOException("Note delete failed (HTTP ${res.code()}).")
    }

    override suspend fun addSharedTask(spaceId: String, title: String, assignee: String?): SharedTask {
        val id = UUID.randomUUID().toString()
        val body = buildJsonObject {
            put("id", id)
            put("space_id", spaceId)
            put("title", title)
            put("assignee", assignee)
            put("sort_order", 0L)
        }
        checked(api().createSharedTask(bearer(), body = body), "Task create failed")
        appendFeed(spaceId, FeedKind.TASK_ADDED, id)
        pullSpace(spaceId)
        return SharedTask(id, spaceId, title, isDone = false, assignee, dueAt = null, sortOrder = 0L)
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
        val id = UUID.randomUUID().toString()
        val cleanTitle = title?.takeIf { it.isNotBlank() }
        checked(
            api().createLink(
                bearer(),
                body = buildJsonObject {
                    put("id", id)
                    put("space_id", spaceId)
                    put("url", trimmed)
                    put("title", cleanTitle)
                },
            ),
            "Link create failed",
        )
        appendFeed(spaceId, FeedKind.LINK_ADDED, id)
        pullSpace(spaceId)
        return SpaceLink(id, spaceId, trimmed, cleanTitle)
    }

    override suspend fun deleteLink(linkId: String) {
        val res = api().deleteLink(bearer(), "eq.$linkId")
        if (!res.isSuccessful) throw IOException("Link delete failed (HTTP ${res.code()}).")
    }

    // -- Phase 8: expanded shared (online-only mirrors) ----------------------

    private data class ExtraCache(
        val messages: List<SpaceMessage> = emptyList(),
        val events: List<SpaceEvent> = emptyList(),
        val files: List<SpaceFile> = emptyList(),
        val feed: List<FeedEntry> = emptyList(),
    )

    private val extras = mutableMapOf<String, MutableStateFlow<ExtraCache>>()

    private fun extraFlow(spaceId: String): MutableStateFlow<ExtraCache> =
        extras.getOrPut(spaceId) { MutableStateFlow(ExtraCache()) }

    override suspend fun pullExtra(spaceId: String) {
        val token = bearer()
        val messages = checked(api().spaceMessages(token, "eq.$spaceId"), "Messages load failed")
        val events = checked(api().spaceEvents(token, "eq.$spaceId"), "Events load failed")
        val files = checked(api().spaceFiles(token, "eq.$spaceId"), "Files load failed")
        val feed = checked(api().spaceFeed(token, "eq.$spaceId"), "Feed load failed")
        extraFlow(spaceId).update { ExtraCache(messages, events, files, feed) }
    }

    override fun observeMessages(spaceId: String): Flow<List<SpaceMessage>> =
        extraFlow(spaceId).map { it.messages }

    override fun observeEvents(spaceId: String): Flow<List<SpaceEvent>> =
        extraFlow(spaceId).map { it.events }

    override fun observeFiles(spaceId: String): Flow<List<SpaceFile>> =
        extraFlow(spaceId).map { it.files }

    override fun observeFeed(spaceId: String): Flow<List<FeedEntry>> =
        extraFlow(spaceId).map { it.feed }

    override suspend fun sendMessage(spaceId: String, body: String) {
        val clean = body.trim()
        require(clean.isNotBlank()) { "Message required." }
        val res = api().sendMessage(
            bearer(),
            body = buildJsonObject {
                put("space_id", spaceId)
                put("author", myId())
                put("body", clean)
            },
        )
        if (!res.isSuccessful) throw IOException("Send failed (HTTP ${res.code()}).")
        pullExtra(spaceId)
    }

    override suspend fun deleteMessage(messageId: String) {
        val res = api().deleteMessage(bearer(), "eq.$messageId")
        if (!res.isSuccessful) throw IOException("Delete failed (HTTP ${res.code()}).")
    }

    override suspend fun addEvent(spaceId: String, title: String, startAt: Long, endAt: Long?): SpaceEvent {
        val clean = title.trim()
        require(clean.isNotBlank()) { "Title required." }
        if (endAt != null) require(endAt >= startAt) { "End must be after start." }
        val id = UUID.randomUUID().toString()
        checked(
            api().createEvent(
                bearer(),
                body = buildJsonObject {
                    put("id", id)
                    put("space_id", spaceId)
                    put("title", clean)
                    put("start_at", startAt)
                    put("end_at", endAt)
                    put("created_by", myId())
                },
            ),
            "Event create failed",
        )
        pullExtra(spaceId)
        return SpaceEvent(id, spaceId, clean, startAt, endAt)
    }

    override suspend fun deleteEvent(eventId: String) {
        val res = api().deleteEvent(bearer(), "eq.$eventId")
        if (!res.isSuccessful) throw IOException("Delete failed (HTTP ${res.code()}).")
    }

    override suspend fun uploadFile(spaceId: String, fileName: String, bytes: ByteArray, mime: String): SpaceFile {
        require(bytes.isNotEmpty()) { "Empty file." }
        require(bytes.size <= MAX_FILE_BYTES) { "File too large (25 MB max)." }
        val safe = fileName.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        val path = "$spaceId/$safe"
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        val putReq = Request.Builder()
            .url("${config.url}/storage/v1/object/space-files/$path")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $token")
            .header("x-upsert", "true")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        http.newCall(putReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Upload failed (HTTP ${resp.code}).")
        }
        val id = UUID.randomUUID().toString()
        val me = myId()
        checked(
            api().createFileRow(
                bearer(),
                body = buildJsonObject {
                    put("id", id)
                    put("space_id", spaceId)
                    put("path", path)
                    put("size", bytes.size.toLong())
                    put("created_by", me)
                },
            ),
            "File record failed",
        )
        pullExtra(spaceId)
        appendFeed(spaceId, "file.added", id)
        return SpaceFile(id, spaceId, path, bytes.size.toLong(), me, "")
    }

    override suspend fun deleteFile(file: SpaceFile) {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        val delReq = Request.Builder()
            .url("${config.url}/storage/v1/object/space-files/${file.path}")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        http.newCall(delReq).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) throw IOException("Delete failed (HTTP ${resp.code}).")
        }
        val res = api().deleteFileRow(bearer(), "eq.${file.id}")
        if (!res.isSuccessful) throw IOException("Record delete failed (HTTP ${res.code()}).")
    }

    override suspend fun downloadFile(file: SpaceFile): ByteArray {
        val token = auth.freshToken() ?: throw IllegalStateException("Not signed in.")
        val signReq = Request.Builder()
            .url("${config.url}/storage/v1/object/sign/space-files/${file.path}")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post("""{"expiresIn":3600}""".toRequestBody("application/json".toMediaType()))
            .build()
        val signedPath = http.newCall(signReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Sign failed (HTTP ${resp.code}).")
            JSONObject(resp.body?.string().orEmpty()).optString("signedURL")
        }
        if (signedPath.isBlank()) throw IOException("Sign failed.")
        val getReq = Request.Builder().url("${config.url}/storage/v1$signedPath").get().build()
        http.newCall(getReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Download failed (HTTP ${resp.code}).")
            return resp.body?.bytes() ?: throw IOException("Empty file.")
        }
    }

    /**
     * Best-effort feed writer: failures are swallowed so content features
     * never break when the feed insert is denied/fails.
     */
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

    private companion object {
        const val MAX_FILE_BYTES = 25 * 1024 * 1024
    }
}
