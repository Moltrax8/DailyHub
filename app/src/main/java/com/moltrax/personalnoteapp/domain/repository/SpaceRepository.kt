package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
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
}
