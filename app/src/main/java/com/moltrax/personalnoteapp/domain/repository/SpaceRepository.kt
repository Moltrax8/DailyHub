package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.FeedEntry
import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceEvent
import com.moltrax.personalnoteapp.domain.model.SpaceFile
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
import com.moltrax.personalnoteapp.domain.model.SpaceMessage
import kotlinx.coroutines.flow.Flow

/**
 * Shared spaces / Duo hubs (Phase 5). Server is truth; Room holds a
 * replace-on-fetch read mirror. Live updates arrive via Realtime (Phase 9);
 * until then screens pull on open + manual refresh.
 */
interface SpaceRepository {
    /** Cached spaces (all types). */
    fun observeSpaces(): Flow<List<Space>>

    /** Pulls my spaces + one space's content into the cache. */
    suspend fun pullSpace(spaceId: String)

    /** Creates a Duo hub with a friend (accepted friendship is the invite). */
    suspend fun createDuo(friendUserId: String, name: String? = null): Space

    suspend fun renameSpace(spaceId: String, name: String?)
    suspend fun deleteSpace(spaceId: String)
    suspend fun inviteMember(spaceId: String, userId: String, asOwner: Boolean = false)
    suspend fun removeMember(spaceId: String, userId: String)
    suspend fun leaveSpace(spaceId: String)

    fun observeMembers(spaceId: String): Flow<List<SpaceMember>>

    /** Pulls then returns members (for find-or-create flows). */
    suspend fun getMembers(spaceId: String): List<SpaceMember>    fun observeNotes(spaceId: String): Flow<List<SharedNote>>
    fun observeTasks(spaceId: String): Flow<List<SharedTask>>
    fun observeLinks(spaceId: String): Flow<List<SpaceLink>>

    suspend fun addNote(spaceId: String, title: String?, bodyMd: String?): SharedNote
    suspend fun editNote(noteId: String, title: String?, bodyMd: String?)
    suspend fun deleteNote(noteId: String)

    suspend fun addSharedTask(spaceId: String, title: String, assignee: String? = null): SharedTask
    suspend fun toggleSharedTask(task: SharedTask)
    suspend fun deleteSharedTask(taskId: String)

    suspend fun addLink(spaceId: String, url: String, title: String?): SpaceLink
    suspend fun deleteLink(linkId: String)

    // -- Phase 8: expanded shared (online-only; pull on open, poll chat) -----

    suspend fun pullExtra(spaceId: String)
    fun observeMessages(spaceId: String): Flow<List<SpaceMessage>>
    fun observeEvents(spaceId: String): Flow<List<SpaceEvent>>
    fun observeFiles(spaceId: String): Flow<List<SpaceFile>>
    fun observeFeed(spaceId: String): Flow<List<FeedEntry>>

    suspend fun sendMessage(spaceId: String, body: String)
    suspend fun deleteMessage(messageId: String)

    suspend fun addEvent(spaceId: String, title: String, startAt: Long, endAt: Long?): SpaceEvent
    suspend fun deleteEvent(eventId: String)

    /** Uploads bytes to space-files and records the row. Returns the entry. */
    suspend fun uploadFile(spaceId: String, fileName: String, bytes: ByteArray, mime: String): SpaceFile
    suspend fun deleteFile(file: SpaceFile)

    /** Downloads bytes via a signed URL (for the viewer). */
    suspend fun downloadFile(file: SpaceFile): ByteArray
}
