package com.moltrax.personalnoteapp.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.dao.CategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskCategoryDao
import com.moltrax.personalnoteapp.data.local.db.dao.TaskDao
import com.moltrax.personalnoteapp.data.local.db.entity.CategoryEntity
import com.moltrax.personalnoteapp.data.local.db.entity.TaskCategoryCrossRef
import com.moltrax.personalnoteapp.data.local.db.entity.TaskEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * xref DAO behavior on a real (in-memory) v20 database: per-category ordering,
 * rename absorb, untagged bucket, and xref-aware orphan-temporary cleanup.
 */
@RunWith(AndroidJUnit4::class)
class TaskCategoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var tasks: TaskDao
    private lateinit var xref: TaskCategoryDao
    private lateinit var cats: CategoryDao

    private fun task(id: String, sort: Long) = TaskEntity(
        id = id, title = id, notes = null, dueDate = null, priority = "MEDIUM",
        isDone = false, isRecurring = false, intervalDays = null,
        focusDurationSeconds = 1500, category = null,
        createdAt = 1000L, updatedAt = 1000L, completedAt = null, sortOrder = sort,
    )

    @Before
    fun open() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tasks = db.taskDao()
        xref = db.taskCategoryDao()
        cats = db.categoryDao()
    }

    @After
    fun close() {
        runBlocking { runCatching { db.close() } }
    }

    @Test
    fun reorderInCategory_rewritesPositions() = runBlocking {
        tasks.upsertAll(listOf(task("a", 0), task("b", 1), task("c", 2)))
        xref.upsertLinks(
            listOf(
                TaskCategoryCrossRef("a", "Work", 2, 0),
                TaskCategoryCrossRef("b", "Work", 0, 0),
                TaskCategoryCrossRef("c", "Work", 1, 0),
            )
        )
        xref.reorderInCategory("Work", listOf("c", "a", "b"))

        val links = xref.getAllLinks().filter { it.categoryName == "Work" }.associateBy({ it.taskId }, { it.sortOrder })
        assertEquals(mapOf("c" to 0L, "a" to 1L, "b" to 2L), links)
        // Global order untouched by category reorder.
        assertEquals(listOf("a", "b", "c"), tasks.getAll().map { it.id })
    }

    @Test
    fun rename_absorbsConflictingLinks() = runBlocking {
        tasks.upsertAll(listOf(task("a", 0), task("b", 1)))
        xref.upsertLinks(
            listOf(
                TaskCategoryCrossRef("a", "Work", 0, 0),
                TaskCategoryCrossRef("a", "Job", 3, 0),
                TaskCategoryCrossRef("b", "Job", 1, 0),
            )
        )
        xref.renameCategoryLinks("Work", "Job")

        val links = xref.getAllLinks()
        // a/Work merged into existing a/Job (kept order 3), b/Job untouched.
        assertEquals(2, links.size)
        assertEquals(3L, links.single { it.taskId == "a" }.sortOrder)
        assertTrue(links.all { it.categoryName == "Job" })
    }

    @Test
    fun untagged_returnsOnlyLinklessTasks() = runBlocking {
        tasks.upsertAll(listOf(task("a", 0), task("b", 1)))
        xref.upsertLinks(listOf(TaskCategoryCrossRef("a", "Work", 0, 0)))

        assertEquals(listOf("b"), xref.getUntagged().map { it.id })
    }

    @Test
    fun orphanCleanup_consultsXref_notLegacyColumn() = runBlocking {
        // Temporary category linked ONLY via xref (legacy column null) must survive.
        tasks.upsertAll(listOf(task("a", 0)))
        xref.upsertLinks(listOf(TaskCategoryCrossRef("a", "Temp", 0, 0)))
        cats.upsert(CategoryEntity("Temp", isPermanent = false))
        cats.upsert(CategoryEntity("Orphan", isPermanent = false))

        cats.deleteOrphanTemporary()

        val remaining = cats.getAll().map { it.name }
        assertTrue("xref-linked Temp must survive", remaining.contains("Temp"))
        assertTrue("unlinked Orphan must be tombstoned", !remaining.contains("Orphan"))
    }
}
