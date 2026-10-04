package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline read-model mirrors of Supabase shared content (Phase 5).
 * The server is the truth (Postgres + RLS); these rows are replaced on every
 * fetch. No tombstones: deletes happen server-side and vanish on next fetch.
 * Timestamps are ISO strings in server format (no converters needed).
 */
@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey val id: String,
    /** DUO | PROJECT (one membership system for both). */
    val type: String,
    val name: String?,
    val createdBy: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "space_members",
    primaryKeys = ["spaceId", "userId"],
)
data class SpaceMemberEntity(
    val spaceId: String,
    val userId: String,
    /** owner | member */
    val role: String,
    val joinedAt: String,
)

@Entity(
    tableName = "shared_notes",
    indices = [Index("spaceId")],
)
data class SharedNoteEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val author: String?,
    val title: String?,
    val bodyMd: String?,
    val updatedAt: String,
)

@Entity(
    tableName = "shared_tasks",
    indices = [Index("spaceId")],
)
data class SharedTaskEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val title: String,
    val isDone: Boolean,
    val assignee: String?,
    val dueAt: Long?,
    val sortOrder: Long,
    val updatedAt: String,
)

@Entity(
    tableName = "space_links",
    indices = [Index("spaceId")],
)
data class SpaceLinkEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val url: String,
    val title: String?,
    val createdBy: String?,
    val createdAt: String,
)
