package com.moltrax.personalnoteapp.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.entity.NoteEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration22To23Test {
    @Test fun preservesTasksAndTags_dropsRemovedData_andRoomValidatesSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-core-${System.nanoTime()}.db"
        val old = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(22) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""CREATE TABLE tasks (
                        id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT, dueDate INTEGER,
                        priority TEXT NOT NULL, isDone INTEGER NOT NULL, isRecurring INTEGER NOT NULL,
                        intervalDays INTEGER, recurrenceType TEXT, recurrenceDaysOfWeek TEXT NOT NULL,
                        focusDurationSeconds INTEGER NOT NULL, category TEXT, subtasks TEXT NOT NULL,
                        createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, completedAt INTEGER,
                        sortOrder INTEGER NOT NULL, linkedWorkoutId TEXT, linkedProgramId TEXT,
                        programStartIndex INTEGER NOT NULL, isPenalty INTEGER NOT NULL, isDeleted INTEGER NOT NULL)""")
                    db.execSQL("CREATE TABLE categories (name TEXT NOT NULL PRIMARY KEY, isPermanent INTEGER NOT NULL, isDeleted INTEGER NOT NULL)")
                    MIGRATION_19_20.migrate(db)
                    MIGRATION_20_21.migrate(db)
                    MIGRATION_21_22.migrate(db)
                    listOf("exercises", "workout_groups", "workouts", "workout_exercises", "workout_sessions").forEach {
                        db.execSQL("CREATE TABLE $it (id TEXT NOT NULL PRIMARY KEY)")
                        db.execSQL("INSERT INTO $it VALUES ('obsolete')")
                    }
                    db.execSQL("INSERT INTO categories VALUES ('Work', 1, 0), ('Gone', 0, 1)")
                    val subtasks = """[{"id":"sub","title":"Checklist","isDone":true}]"""
                    db.execSQL("INSERT INTO tasks VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf("keep", "Title", "Task notes", 123456L, "MEDIUM", 0, 1, null, "WEEKLY", "[1,3]", 1500,
                            "Work", subtasks, 10L, 20L, null, -7L, "removed", null, 0, 0, 0))
                    db.execSQL("INSERT INTO tasks SELECT 'deleted', title, notes, dueDate, priority, isDone, isRecurring, intervalDays, recurrenceType, recurrenceDaysOfWeek, focusDurationSeconds, category, subtasks, createdAt, updatedAt, completedAt, sortOrder, linkedWorkoutId, linkedProgramId, programStartIndex, isPenalty, 1 FROM tasks WHERE id = 'keep'")
                    db.execSQL("INSERT INTO task_category_cross_ref VALUES ('keep','Work',5,9), ('keep','Gone',6,9), ('deleted','Work',7,9)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            old.writableDatabase
            old.close()
            val room = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(MIGRATION_22_23).build()
            try {
                val task = room.taskDao().getAll().single()
                assertEquals("keep", task.id)
                assertEquals("Task notes", task.notes)
                assertEquals(-7L, task.sortOrder)
                assertEquals(listOf(1, 3), task.recurrenceDaysOfWeek)
                assertEquals("Checklist", task.subtasks.single().title)
                assertTrue(task.subtasks.single().isDone)
                assertEquals(listOf("Work"), room.categoryDao().getAll().map { it.name })
                assertEquals(5L, room.taskCategoryDao().getAllLinks().single().sortOrder)
                room.noteDao().upsert(NoteEntity("note", "Title", "Body", true, 1, 2))
                assertEquals("Body", room.noteDao().getById("note")!!.body)
                val tables = mutableListOf<String>()
                room.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
                    while (cursor.moveToNext()) tables += cursor.getString(0)
                }
                assertFalse(tables.any { it.startsWith("workout") || it.startsWith("project") || it.startsWith("shared_") || it.startsWith("space") })
                assertFalse("exercises" in tables)
            } finally { room.close() }
        } finally { old.close(); context.deleteDatabase(name) }
    }
}
