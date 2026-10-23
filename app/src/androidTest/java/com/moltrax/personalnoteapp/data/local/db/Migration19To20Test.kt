package com.moltrax.personalnoteapp.data.local.db

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * MIGRATION_19_20 test without Room codegen in androidTest: builds a minimal
 * v19-layout file DB with raw SQLite, runs the REAL migration object, and
 * asserts the backfill (legacy tasks.category → xref, ordering preserved).
 */
@RunWith(AndroidJUnit4::class)
class Migration19To20Test {

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun createV19File() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("mig-19-20-test.db")
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name("mig-19-20-test.db")
                .callback(object : SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY, " +
                                "title TEXT NOT NULL, category TEXT, " +
                                "sortOrder INTEGER NOT NULL DEFAULT 0, " +
                                "createdAt INTEGER NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: androidx.sqlite.db.SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase
        // Two tagged tasks (order 5, 6) + one untagged + one empty-string tag (ignored).
        db.execSQL("INSERT INTO tasks (id, title, category, sortOrder, createdAt) VALUES ('t1', 'A', 'Work', 5, 100)")
        db.execSQL("INSERT INTO tasks (id, title, category, sortOrder, createdAt) VALUES ('t2', 'B', 'Work', 6, 200)")
        db.execSQL("INSERT INTO tasks (id, title, category, sortOrder, createdAt) VALUES ('t3', 'C', NULL, 7, 300)")
        db.execSQL("INSERT INTO tasks (id, title, category, sortOrder, createdAt) VALUES ('t4', 'D', '', 8, 400)")
    }

    @After
    fun dropFile() {
        runCatching { helper.close() }
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase("mig-19-20-test.db")
    }

    @Test
    fun backfill_createsOneLinkPerLegacyTag_preservingOrder() {
        val db = helper.writableDatabase
        MIGRATION_19_20.migrate(db)
        db.version = 20

        db.query("SELECT taskId, categoryName, sortOrder, addedAt FROM task_category_cross_ref ORDER BY sortOrder").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("t1", c.getString(0))
            assertEquals("Work", c.getString(1))
            assertEquals(5L, c.getLong(2))
            assertEquals(100L, c.getLong(3))
            c.moveToNext()
            assertEquals("t2", c.getString(0))
            assertEquals("Work", c.getString(1))
            assertEquals(6L, c.getLong(2))
            assertEquals(200L, c.getLong(3))
        }
        // Room-default index names (schema validation depends on them).
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'task_category_cross_ref' ORDER BY name"
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(0)
            assertTrue(names.contains("index_task_category_cross_ref_taskId"))
            assertTrue(names.contains("index_task_category_cross_ref_categoryName"))
            // sqlite_autoindex for the composite PK.
            assertTrue(names.any { it.startsWith("sqlite_autoindex") })
        }
    }
}
