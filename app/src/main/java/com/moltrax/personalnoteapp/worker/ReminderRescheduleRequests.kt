package com.moltrax.personalnoteapp.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder

/** The caller supplies its WorkManager enqueue method and an explicit lifecycle-specific policy. */
internal fun enqueueReminderReschedule(
    policy: ExistingWorkPolicy,
    enqueue: (String, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit,
) {
    enqueue(
        RescheduleNotificationsWorker.WORK_NAME,
        policy,
        OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build(),
    )
}
