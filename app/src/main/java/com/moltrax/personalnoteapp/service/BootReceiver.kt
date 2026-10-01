package com.moltrax.personalnoteapp.service

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moltrax.personalnoteapp.worker.RescheduleNotificationsWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // Sistem alarm deposu bu olaylarda sıfırlanabilir/kayabilir: yeniden kurmayı tekilleştirilmiş
        // işle (KEEP) tetikle; çift kuyruklanma olmaz.
        val reschedule = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ||
            // OEM hızlı başlatma (bazı cihazlarda BOOT_COMPLETED yerine geçer).
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!reschedule) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            RescheduleNotificationsWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build(),
        )
    }
}
