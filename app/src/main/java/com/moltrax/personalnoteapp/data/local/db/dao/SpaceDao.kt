package com.moltrax.personalnoteapp.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.moltrax.personalnoteapp.data.local.db.entity.SharedNoteEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SharedTaskEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceLinkEntity
import com.moltrax.personalnoteapp.data.local.db.entity.SpaceMemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Offline cache for shared spaces (Phase 5). All reads are per-space flows;
 * [replaceSpaceCache] swaps one space's mirror after a server fetch.
 */
@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces ORDER BY createdAt DESC")
    fun observeSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getSpace(id: String): SpaceEntity?

    @Query("SELECT * FROM space_members WHERE spaceId = :spaceId ORDER BY joinedAt ASC")
    fun observeMembers(spaceId: String): Flow<List<SpaceMemberEntity>>

    @Query("SELECT * FROM shared_notes WHERE spaceId = :spaceId ORDER BY updatedAt DESC")
    fun observeNotes(spaceId: String): Flow<List<SharedNoteEntity>>

    @Query("SELECT * FROM shared_tasks WHERE spaceId = :spaceId ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeSharedTasks(spaceId: String): Flow<List<SharedTaskEntity>>

    @Query("SELECT * FROM space_links WHERE spaceId = :spaceId ORDER BY createdAt DESC")
    fun observeLinks(spaceId: String): Flow<List<SpaceLinkEntity>>

    @Upsert
    suspend fun upsertSpaces(spaces: List<SpaceEntity>)

    @Transaction
    suspend fun replaceSpaceCache(
        space: SpaceEntity,
        members: List<SpaceMemberEntity>,
        notes: List<SharedNoteEntity>,
        tasks: List<SharedTaskEntity>,
        links: List<SpaceLinkEntity>,
    ) {
        upsertSpaces(listOf(space))
        deleteMembers(space.id)
        deleteNotes(space.id)
        deleteTasks(space.id)
        deleteLinks(space.id)
        upsertMembers(members)
        upsertNotes(notes)
        upsertTasks(tasks)
        upsertLinks(links)
    }

    @Upsert
    suspend fun upsertMembers(members: List<SpaceMemberEntity>)

    @Upsert
    suspend fun upsertNotes(notes: List<SharedNoteEntity>)

    @Upsert
    suspend fun upsertTasks(tasks: List<SharedTaskEntity>)

    @Upsert
    suspend fun upsertLinks(links: List<SpaceLinkEntity>)

    @Query("DELETE FROM space_members WHERE spaceId = :spaceId")
    suspend fun deleteMembers(spaceId: String)

    @Query("DELETE FROM shared_notes WHERE spaceId = :spaceId")
    suspend fun deleteNotes(spaceId: String)

    @Query("DELETE FROM shared_tasks WHERE spaceId = :spaceId")
    suspend fun deleteTasks(spaceId: String)

    @Query("DELETE FROM space_links WHERE spaceId = :spaceId")
    suspend fun deleteLinks(spaceId: String)

    /** Drops a left/deleted space from the cache entirely. */
    @Transaction
    suspend fun evictSpace(spaceId: String) {
        deleteMembers(spaceId)
        deleteNotes(spaceId)
        deleteTasks(spaceId)
        deleteLinks(spaceId)
        deleteSpace(spaceId)
    }

    @Query("DELETE FROM spaces WHERE id = :spaceId")
    suspend fun deleteSpace(spaceId: String)
}
