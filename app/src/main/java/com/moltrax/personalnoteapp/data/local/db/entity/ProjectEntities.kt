package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline read-model mirrors of Supabase project content (Phase 6).
 * Same contract as spaces cache: server is truth, replaced on every fetch.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val spaceId: String,
    val descriptionMd: String?,
    val boardColumns: List<String>,
)

@Entity(
    tableName = "project_items",
    indices = [Index("spaceId")],
)
data class ProjectItemEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val title: String,
    /** Idea | Planned | Developing | Finished */
    val status: String,
    val bodyMd: String?,
    val linkedUrl: String?,
    val sortOrder: Long,
    val updatedAt: String,
)

@Entity(
    tableName = "project_comments",
    indices = [Index("spaceId")],
)
data class ProjectCommentEntity(
    @PrimaryKey val id: String,
    val spaceId: String,
    val refType: String?,
    val refId: String?,
    val author: String?,
    val bodyMd: String?,
    val createdAt: String,
)
