package com.moltrax.personalnoteapp.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReminderChannelCallerTest {
    @get:Rule val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val prefs = AppPreferences(context)
    private val taskId = "channel-test-${UUID.randomUUID()}"
    private var previousChannel: NotificationChannel? = null
    private var previousLegacy: NotificationChannel? = null
    private lateinit var previousLanguage: String

    @Before fun rememberSettings() = runBlocking {
        previousChannel = manager.getNotificationChannel("task_reminders")
        previousLegacy = manager.getNotificationChannel("system_messages")
        previousLanguage = prefs.language.first()
    }

    @After fun restoreSettings(): Unit = runBlocking {
        manager.cancel(taskId, 0)
        manager.deleteNotificationChannel("task_reminders")
        manager.deleteNotificationChannel("system_messages")
        previousChannel?.let { manager.createNotificationChannel(it) }
        previousLegacy?.let { manager.createNotificationChannel(it) }
        prefs.setLanguage(previousLanguage)
    }

    @Test fun serviceCreatesReminderChannelAndRemovesOnlyTheLegacyChannel() {
        manager.deleteNotificationChannel("task_reminders")
        manager.createNotificationChannel(NotificationChannel("system_messages", "Legacy", NotificationManager.IMPORTANCE_LOW))
        NotificationService(context)
        val channel = manager.getNotificationChannel("task_reminders")!!
        assertEquals("Task Reminders", channel.name.toString())
        assertEquals(context.getString(R.string.notif_channel_description), channel.description)
        assertTrue(channel.canShowBadge())
        assertNull(manager.getNotificationChannel("system_messages"))
    }

    @Test fun serviceRefreshesExistingDescriptionAndBadgeWithoutChangingImportance() {
        manager.createNotificationChannel(NotificationChannel("task_reminders", "Task Reminders", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Existing description"
            setShowBadge(false)
        })
        val importance = manager.getNotificationChannel("task_reminders")!!.importance
        NotificationService(context)
        val channel = manager.getNotificationChannel("task_reminders")!!
        assertEquals(importance, channel.importance)
        assertEquals(context.getString(R.string.notif_channel_description), channel.description)
        assertTrue(channel.canShowBadge())
    }

    @Test fun receiverCreatesMissingChannelUsingTheSavedApplicationLanguage() = runBlocking {
        prefs.setLanguage("tr")
        manager.deleteNotificationChannel("task_reminders")
        deliverReminder()
        val channel = manager.getNotificationChannel("task_reminders")!!
        assertEquals(context.localizedFor("tr").getString(R.string.notif_channel_description), channel.description)
        assertTrue(channel.canShowBadge())
        assertTrue(manager.activeNotifications.any { it.tag == taskId && it.notification.channelId == "task_reminders" })
    }

    @Test fun receiverLeavesExistingMetadataAndLegacyChannelUntouched() {
        manager.createNotificationChannel(NotificationChannel("task_reminders", "Task Reminders", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Do not refresh this description"
            setShowBadge(false)
        })
        manager.createNotificationChannel(NotificationChannel("system_messages", "Legacy", NotificationManager.IMPORTANCE_LOW))
        val before = manager.getNotificationChannel("task_reminders")!!
        deliverReminder()
        val after = manager.getNotificationChannel("task_reminders")!!
        assertEquals(before.description, after.description)
        assertEquals(before.importance, after.importance)
        assertEquals(before.canShowBadge(), after.canShowBadge())
        assertNotNull(manager.getNotificationChannel("system_messages"))
    }

    private fun deliverReminder() {
        val completed = CountDownLatch(1)
        val intent = Intent(context, NotificationBroadcastReceiver::class.java)
            .putExtra("task_id", taskId).putExtra("task_title", "Channel regression test").putExtra("reminder_minutes", 0)
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { completed.countDown() }
        }, Handler(Looper.getMainLooper()), 0, null, null)
        assertTrue("Reminder receiver did not finish its async work", completed.await(10, TimeUnit.SECONDS))
    }
}
