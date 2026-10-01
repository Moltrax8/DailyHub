package com.moltrax.personalnoteapp.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID      = "task_reminders"
private const val CHANNEL_NAME    = "Task Reminders"

const val ACTION_TASK_REMINDER = "com.moltrax.personalnoteapp.ACTION_TASK_REMINDER"

/**
 * Task id (UUID string) -> kararlı int istek/bildirim kodu eşlemesi.
 * `String.hashCode()` çakışabilir; bunun yerine ilk kullanımda artımlı bir kimlik verip
 * SharedPreferences'ta kalıcı tutuyoruz — böylece alarmlar ve bildirimler birbirine karışmaz.
 */
object NotificationIds {
    private const val PREFS = "notification_ids"
    private const val KEY_NEXT = "next_id"

    @Synchronized
    fun stableId(context: Context, taskId: String): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getInt(keyFor(taskId), -1).takeIf { it > 0 }?.let { return it }
        var next = prefs.getInt(KEY_NEXT, 1).coerceAtLeast(1)
        // Olası elle çakışmaya karşı boş slot ara (pratikte tek geçiş yeter).
        while (prefs.all.values.any { it == next }) next++
        prefs.edit().putInt(keyFor(taskId), next).putInt(KEY_NEXT, next + 1).apply()
        return next
    }

    private fun keyFor(taskId: String) = "nid_" + Uri.encode(taskId)
}

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    init { createChannels() }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_description)
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        } else {
            // Mevcut kanalda açıklamayı/rozeti tazele (kanal silinmeden güncellenebilir alanlar).
            nm.getNotificationChannel(CHANNEL_ID)?.let {
                it.description = context.getString(R.string.notif_channel_description)
                it.setShowBadge(true)
                nm.createNotificationChannel(it)
            }
        }
        // Oyunlaştırma kaldırıldı: eski "Sistem Mesajları" (Ceza Bölgesi) kanalını temizle.
        nm.deleteNotificationChannel("system_messages")
    }

    /** Android 12+ kesin alarm izni verilmiş mi (alt sürümlerde her zaman true). */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Kullanıcıyı sistemin kesin-alarm izin ekranına yönlendiren intent. */
    fun exactAlarmSettingsIntent(): Intent =
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Bildirim izni (Android 13+ POST_NOTIFICATIONS dahil) açık mı. */
    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Uygulamanın sistem bildirim ayarları ekranına giden intent. */
    fun appNotificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun alarmIntent(taskId: String): Intent =
        Intent(context, NotificationBroadcastReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            data = Uri.parse("personalnoteapp://reminder/${Uri.encode(taskId)}")
        }

    /**
     * Hatırlatma alarmını kurar. Kesin alarm izni varsa [setExactAndAllowWhileIdle], yoksa
     * inexact [setAndAllowWhileIdle] kullanılır (gecikebilir — arayüz [canScheduleExactAlarms]
     * üzerinden kullanıcıyı izin ekranına yönlendirmelidir).
     * @return true = kesin alarm kuruldu, false = inexact yedeğe düşüldü.
     */
    fun scheduleReminder(task: Task, reminderMinutes: Int = 60): Boolean {
        if (task.dueDate == null) return false
        val now = System.currentTimeMillis()
        val due = task.dueDate
        if (due <= now) return false

        val triggerAt = due - reminderMinutes * 60_000L
        val fireAt    = if (triggerAt > now) triggerAt else due
        if (fireAt <= now) return false

        // Bildirim metni GERÇEKTEN kalan süreyi göstersin: hatırlatma penceresi zaten geçmişse
        // (fireAt = due) bu 0 olur ("çok kısa" metni), aksi halde reminderMinutes. Böylece bitişte
        // tetiklenen bir bildirim yanlışlıkla "1 saat kaldı" demez.
        val minutesLeftAtFire = ((due - fireAt) / 60_000L).toInt()

        val notifIntent = alarmIntent(task.id).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("reminder_minutes", minutesLeftAtFire)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            NotificationIds.stableId(context, task.id),
            notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
        return exact
    }

    fun cancelReminder(taskId: String) {
        val pi = PendingIntent.getBroadcast(
            context,
            NotificationIds.stableId(context, taskId),
            alarmIntent(taskId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: run {
            // Eski sürümden kalma hashCode tabanlı PendingIntent'i de temizlemeyi dene (geçiş).
            cancelLegacyReminder(taskId)
            return
        }
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    /** Toplu iptal: uyarılar kapatıldığında/yeniden kurulurken sahipsiz alarm bırakmaz. */
    fun cancelAll(taskIds: Collection<String>) {
        taskIds.forEach { cancelReminder(it) }
    }

    /** hashCode() döneminden kalma alarmları temizler (tek seferlik geçiş yardımı). */
    private fun cancelLegacyReminder(taskId: String) {
        runCatching {
            val legacy = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                Intent(context, NotificationBroadcastReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            context.getSystemService(AlarmManager::class.java).cancel(legacy)
            legacy.cancel()
        }
    }
}
