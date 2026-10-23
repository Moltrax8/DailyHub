package com.moltrax.personalnoteapp.data.repository

import com.moltrax.personalnoteapp.data.local.db.dao.NoteDao
import com.moltrax.personalnoteapp.data.local.db.entity.toDomain
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.domain.model.Note
import com.moltrax.personalnoteapp.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NoteRepository {

    override fun observeAll(): Flow<List<Note>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Note? = dao.getById(id)?.toDomain()

    override suspend fun upsert(note: Note) = dao.upsert(note.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)
}
