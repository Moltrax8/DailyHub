package com.moltrax.personalnoteapp.domain.repository

import com.moltrax.personalnoteapp.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeAll(): Flow<List<Note>>
    suspend fun getById(id: String): Note?
    suspend fun upsert(note: Note)
    suspend fun delete(id: String)
}
