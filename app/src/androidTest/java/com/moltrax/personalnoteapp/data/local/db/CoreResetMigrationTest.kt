package com.moltrax.personalnoteapp.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite -> Room validation, not just assertions about migration SQL text. */
@RunWith(AndroidJUnit4::class)
class CoreResetMigrationTest {
    @Test fun upgradePreservesLocalContentAndRemovesRetiredData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "core-reset-migration-test.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""CREATE TABLE tasks (
                            id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT, dueDate INTEGER,
                            priority TEXT NOT NULL, isDone INTEGER NOT NULL, isRecurring INTEGER NOT NULL,
                            intervalDays INTEGER, recurrenceType TEXT, recurrenceDaysOfWeek TEXT NOT NULL,
                            focusDurationSeconds INTEGER NOT NULL, category TEXT, subtasks TEXT NOT NULL,
                            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, completedAt INTEGER,
                            sortOrder INTEGER NOT NULL, isDeleted INTEGER NOT NULL, linkedWorkoutId TEXT,
                            linkedProgramId TEXT, programStartIndex INTEGER NOT NULL, isSynced INTEGER NOT NULL,
                            isPenalty INTEGER NOT NULL)""")
                        db.execSQL("CREATE TABLE categories (name TEXT NOT NULL PRIMARY KEY, isPermanent INTEGER NOT NULL, isDeleted INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE task_category_cross_ref (taskId TEXT NOT NULL, categoryName TEXT NOT NULL, sortOrder INTEGER NOT NULL DEFAULT 0, addedAt INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(taskId, categoryName))")
                        db.execSQL("CREATE INDEX index_task_category_cross_ref_taskId ON task_category_cross_ref(taskId)")
                        db.execSQL("CREATE INDEX index_task_category_cross_ref_categoryName ON task_category_cross_ref(categoryName)")
                        listOf("workout_sessions", "workout_exercises", "workouts", "workout_groups", "exercises",
                            "space_members", "shared_notes", "shared_tasks", "space_links", "spaces",
                            "project_comments", "project_items", "projects").forEach {
                            db.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY)")
                        }
                        val checklist = """[{"id":"sub1","title":"Keep me","isDone":false},{"id":"sub2","title":"Done","isDone":true}]"""
                        fun insert(id: String, deleted: Int, done: Int) {
                            db.execSQL("INSERT INTO tasks VALUES (?, ?, ?, 5000, 'MEDIUM', ?, 1, 2, 'WEEKLY', '[1,5]', 1500, 'Work', ?, 100, 200, NULL, -7, ?, 'old-workout', NULL, 0, 0, 0)",
                                arrayOf(id, "Task $id", "Preserved notes", done, checklist, deleted))
                        }
                        insert("active", 0, 0)
                        insert("done", 0, 1)
                        insert("deleted", 1, 0)
                        db.execSQL("INSERT INTO categories VALUES ('Work', 1, 0), ('Removed', 1, 1)")
                        db.execSQL("INSERT INTO task_category_cross_ref VALUES ('active','Work',42,100), ('active','LooseTag',9,100), ('active','Removed',0,100), ('deleted','Work',1,100)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper.writableDatabase
        helper.close()
        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_22_23).build()
        try {
            val tasks = room.taskDao().getAll()
            assertEquals(setOf("active", "done"), tasks.map { it.id }.toSet())
            val task = tasks.single { it.id == "active" }
            assertEquals("Preserved notes", task.notes)
            assertEquals(-7L, task.sortOrder)
            assertEquals(listOf(1, 5), task.recurrenceDaysOfWeek)
            assertEquals(2, task.subtasks.size)
            assertTrue(task.subtasks[1].isDone)
            val links = room.taskCategoryDao().getAllLinks()
            assertEquals(setOf("Work", "LooseTag"), links.map { it.categoryName }.toSet())
            assertEquals(42L, links.single { it.categoryName == "Work" }.sortOrder)
            assertTrue(room.noteDao().observeAll().first().isEmpty())
            room.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                val tables = buildSet { while (c.moveToNext()) add(c.getString(0)) }
                assertFalse("workouts" in tables)
                assertFalse("spaces" in tables)
                assertFalse("projects" in tables)
            }
        } finally {
            room.close()
            context.deleteDatabase(name)
        }
    }
}
