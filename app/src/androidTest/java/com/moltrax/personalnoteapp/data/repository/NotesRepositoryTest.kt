package com.moltrax.personalnoteapp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.domain.model.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesRepositoryTest {
    @Test fun localNotes_keepTitleBodyPinAndOrder_andHardDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = NoteRepositoryImpl(db.noteDao())
            repo.upsert(Note("old", "Title", "Body", true, 1, 1))
            repo.upsert(Note("new", "Other", "Second", false, 1, 20))
            assertEquals(listOf("old", "new"), repo.observeAll().first().map { it.id })
            val note = repo.getById("old")!!
            assertEquals("Body", note.body)
            repo.upsert(note.copy(title = "Edited", isPinned = false, updatedAt = 10))
            assertEquals(listOf("new", "old"), repo.observeAll().first().map { it.id })
            repo.delete("old")
            assertNull(repo.getById("old"))
            assertEquals(1, repo.observeAll().first().size)
        } finally { db.close() }
    }
}
