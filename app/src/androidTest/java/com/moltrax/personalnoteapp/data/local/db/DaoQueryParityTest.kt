package com.moltrax.personalnoteapp.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import com.moltrax.personalnoteapp.data.local.db.entity.TaskCategoryCrossRef
import com.moltrax.personalnoteapp.data.local.db.entity.toEntity
import com.moltrax.personalnoteapp.domain.model.Task
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoQueryParityTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }

    @After fun close() { db.close() }

    @Test fun taskSnapshotAndFlowKeepBothSortKeysAndInvalidateAfterAnUpdate() = runBlocking {
        val dao = db.taskDao()
        val older = Task(id = "older", title = "Older", sortOrder = 5, createdAt = 10).toEntity()
        dao.upsertAll(listOf(older, Task(id = "newer", title = "Newer", sortOrder = 5, createdAt = 20).toEntity(),
            Task(id = "first", title = "First", sortOrder = -1, createdAt = 0).toEntity()))
        val first = CompletableDeferred<List<String>>()
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            dao.observeAll().distinctUntilChanged().onEach { first.complete(it.map { row -> row.id }) }.take(2).toList()
        }
        assertEquals(listOf("first", "newer", "older"), withTimeout(5_000) { first.await() })
        assertEquals(listOf("first", "newer", "older"), dao.getAll().map { it.id })
        dao.upsert(older.copy(sortOrder = -2))
        assertEquals(listOf("older", "first", "newer"), withTimeout(5_000) { emissions.await() }.last().map { it.id })
        assertEquals(listOf("older", "first", "newer"), dao.getAll().map { it.id })
    }

    @Test fun categorySnapshotAndFlowKeepNoCaseOrderingAndInvalidateAfterAnInsert() = runBlocking {
        val dao = db.categoryDao()
        listOf("zebra", "bravo", "Alpha").forEach { dao.upsert(CategoryEntity(it)) }
        val first = CompletableDeferred<List<String>>()
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            dao.observeAll().distinctUntilChanged().onEach { first.complete(it.map { row -> row.name }) }.take(2).toList()
        }
        assertEquals(listOf("Alpha", "bravo", "zebra"), withTimeout(5_000) { first.await() })
        assertEquals(listOf("Alpha", "bravo", "zebra"), dao.getAll().map { it.name })
        dao.upsert(CategoryEntity("aardvark"))
        assertEquals(listOf("aardvark", "Alpha", "bravo", "zebra"),
            withTimeout(5_000) { emissions.await() }.last().map { it.name })
        assertEquals(listOf("aardvark", "Alpha", "bravo", "zebra"), dao.getAll().map { it.name })
    }

    @Test fun linkSnapshotAndFlowKeepAllFieldsAndInvalidateAfterAnInsert() = runBlocking {
        val dao = db.taskCategoryDao()
        val original = setOf(TaskCategoryCrossRef("a", "Work", 9, 100), TaskCategoryCrossRef("b", "Home", -2, 200))
        dao.upsertLinks(original.toList())
        val first = CompletableDeferred<Set<TaskCategoryCrossRef>>()
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            dao.observeAllLinks().distinctUntilChanged().onEach { first.complete(it.toSet()) }.take(2).toList()
        }
        assertEquals(original, withTimeout(5_000) { first.await() })
        assertEquals(original, dao.getAllLinks().toSet())
        val added = TaskCategoryCrossRef("a", "Home", 3, 300)
        dao.upsertLinks(listOf(added))
        assertEquals(original + added, withTimeout(5_000) { emissions.await() }.last().toSet())
        assertEquals(original + added, dao.getAllLinks().toSet())
    }
}
