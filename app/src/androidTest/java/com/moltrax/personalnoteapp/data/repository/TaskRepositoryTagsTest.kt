package com.moltrax.personalnoteapp.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.service.NotificationScheduler
import com.moltrax.personalnoteapp.service.NotificationService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tag flows through the real repository stack (Phase 2): upsert writes links,
 * positions survive edits, single-category views follow link order, ANY/ALL
 * matching works, and replaceAll rewrites links from the payload.
 */
@RunWith(AndroidJUnit4::class)
class TaskRepositoryTagsTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepositoryImpl

    private fun task(id: String, sort: Long, tags: Set<String> = emptySet()) = Task(
        id = id, title = id, sortOrder = sort, categoryNames = tags,
    )

    @Before
    fun open() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = AppPreferences(ctx)
        val svc = NotificationService(ctx)
        repo = TaskRepositoryImpl(db.taskDao(), db.taskCategoryDao(), db, prefs, NotificationScheduler(svc, prefs))
    }

    @After
    fun close() {
        runBlocking { runCatching { db.close() } }
    }

    @Test
    fun upsert_writesLinks_andReadsThemBack() {
        runBlocking {
            repo.upsert(task("a", 0, setOf("Work", "Home")))

            val got = repo.getById("a")!!
            assertEquals(setOf("Work", "Home"), got.categoryNames)
            // Legacy column carries the deterministic primary tag.
            assertEquals("Home", db.taskDao().getById("a")!!.category)
        }
    }

    @Test
    fun setTaskCategories_replacesLinks_keepingPositions() {
        runBlocking {
            repo.upsert(task("a", 0, setOf("Work", "Home")))
            repo.setTaskCategories("a", setOf("Home", "Fun"))

            val got = repo.getById("a")!!
            assertEquals(setOf("Home", "Fun"), got.categoryNames)
        }
    }

    @Test
    fun singleCategoryView_followsLinkOrder_notGlobalOrder() {
        runBlocking {
            repo.upsert(task("a", 0, setOf("Work")))
            repo.upsert(task("b", 1, setOf("Work")))
            repo.upsert(task("c", 2, setOf("Work")))
            repo.reorderInCategory("Work", listOf("c", "a", "b"))

            val view = repo.observeTasksForCategory("Work").first().map { it.id }
            assertEquals(listOf("c", "a", "b"), view)
            // Global order untouched.
            assertEquals(listOf("a", "b", "c"), repo.getAll().map { it.id })
        }
    }

    @Test
    fun multiTagView_anyAndAllMatch() {
        runBlocking {
            repo.upsert(task("a", 0, setOf("Work", "Home")))
            repo.upsert(task("b", 1, setOf("Work")))
            repo.upsert(task("c", 2, setOf()))

            val any = repo.observeTasksForCategories(setOf("Home", "Nope"), matchAll = false).first()
            assertEquals(listOf("a"), any.map { it.id })
            val all = repo.observeTasksForCategories(setOf("Work", "Home"), matchAll = true).first()
            assertEquals(listOf("a"), all.map { it.id })
            assertEquals(listOf("c"), repo.getUntagged().map { it.id })
        }
    }

    @Test
    fun replaceAll_rewritesLinksFromPayload() {
        runBlocking {
            repo.upsert(task("a", 0, setOf("Work")))
            repo.replaceAll(listOf(task("a", 0, setOf("Fun")), task("b", 1, setOf())))

            assertEquals(setOf("Fun"), repo.getById("a")!!.categoryNames)
            assertTrue(repo.getById("b")!!.categoryNames.isEmpty())
        }
    }
}
