package com.moltrax.personalnoteapp.service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.moltrax.personalnoteapp.data.local.db.AppDatabase
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.repository.TaskRepositoryImpl
import com.moltrax.personalnoteapp.domain.model.Task
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * End-to-end reminder test on a real device/emulator with real components —
 * real Room (in-memory instance of the app's [AppDatabase]) →
 * real [TaskRepositoryImpl] → real [NotificationScheduler] →
 * real [NotificationService] → real AlarmManager. Alarm existence is asserted
 * via [NotificationService.isReminderScheduled] (PendingIntent lookup).
 *
 * No Hilt test plumbing: the chain is constructed by hand in [buildRealChain],
 * which also keeps this test independent of the KSP/androidTest processing setup.
 */
@RunWith(AndroidJUnit4::class)
class NotificationReminderE2ETest {

    private val hourMs = 60L * 60 * 1000
    private val createdIds = mutableListOf<String>()

    private lateinit var db: AppDatabase
    private lateinit var repo: TaskRepositoryImpl
    private lateinit var svc: NotificationService

    @Before
    fun buildRealChain() {
        runBlocking {
            val ctx: Context = ApplicationProvider.getApplicationContext()
            db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val prefs = AppPreferences(ctx)
            prefs.setSystemAlertsEnabled(true)
            svc = NotificationService(ctx)
            val scheduler = NotificationScheduler(svc, prefs)
            repo = TaskRepositoryImpl(db.taskDao(), prefs, scheduler)
        }
    }

    /** Cleanup through the real delete path (also exercises cancel-on-delete). */
    @After
    fun cleanup() {
        runBlocking {
            createdIds.toList().forEach { runCatching { repo.delete(it) } }
            createdIds.clear()
            runCatching { db.close() }
        }
    }

    private fun futureTask(dueInMs: Long = 2 * hourMs, isDone: Boolean = false) = Task(
        title = "E2E reminder",
        dueDate = System.currentTimeMillis() + dueInMs,
        isDone = isDone,
    )

    @Test
    fun upsertFutureTask_stagesAlarm_delete_cancelsIt() {
        runBlocking {
            val task = futureTask()
            createdIds += task.id

            repo.upsert(task)
            assertTrue("alarm must be staged after upsert", svc.isReminderScheduled(task.id))

            val diag = svc.lastReminder()
            assertNotNull(diag)
            assertEquals(task.id, diag!!.taskId)
            // Task due in 2h with 60min lead → fires ~1h from now.
            assertTrue("diag fireAt off", abs(diag.fireAt - (task.dueDate!! - hourMs)) < 60_000L)

            repo.delete(task.id)
            assertFalse("alarm must be gone after delete", svc.isReminderScheduled(task.id))
        }
    }

    @Test
    fun doneTask_neverStagesAlarm() {
        runBlocking {
            val task = futureTask(isDone = true)
            createdIds += task.id

            repo.upsert(task)
            assertFalse("done task must not stage an alarm", svc.isReminderScheduled(task.id))
        }
    }

    @Test
    fun datelessTask_neverStagesAlarm() {
        runBlocking {
            val task = Task(title = "E2E dateless")
            createdIds += task.id

            repo.upsert(task)
            assertFalse("dateless task must not stage an alarm", svc.isReminderScheduled(task.id))
        }
    }

    @Test
    fun replaceAll_sweepsVanishedAlarms() {
        runBlocking {
            val oldTask = futureTask()
            val newTask = futureTask()
            createdIds += newTask.id

            repo.upsert(oldTask)
            assertTrue(svc.isReminderScheduled(oldTask.id))

            repo.replaceAll(listOf(newTask))
            assertFalse("vanished row must lose its alarm", svc.isReminderScheduled(oldTask.id))
            assertTrue("kept row must be (re)scheduled", svc.isReminderScheduled(newTask.id))
        }
    }
}
