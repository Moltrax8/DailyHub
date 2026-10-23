package com.moltrax.personalnoteapp.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moltrax.personalnoteapp.domain.model.Note

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val isPinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

fun NoteEntity.toDomain() = Note(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Note.toEntity() = NoteEntity(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
