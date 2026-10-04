package com.moltrax.personalnoteapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.widget.TaskWidget
import com.moltrax.personalnoteapp.worker.RescheduleNotificationsWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PersonalNoteApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var taskRepository: TaskRepository

    // Lightweight process-lifetime scope: listens to the task flow and refreshes the widget.
    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        val wm = WorkManager.getInstance(this)

        // Gamification removed: cancel the "Penalty Zone" periodic job installed by older versions
        // (the worker class no longer exists; otherwise WorkManager would try to start it and produce errors).
        wm.cancelUniqueWork("penalty_check_periodic")

        // Re-install reminder alarms for pending tasks on every app launch
        // (safety net if the device rebooted or alarms were dropped). Deduplicated job
        // (KEEP): no duplicate enqueueing during a cold-start storm.
        wm.enqueueUniqueWork(
            RescheduleNotificationsWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build(),
        )

        // Auto-refresh the widget whenever task data (including subtasks) changes — SINGLE source.
        // Glance widgets cannot listen to the flow on their own; they only redraw when updateAll
        // is called. The signature covers title/completion/notes/order/due/category/links + subtask state
        // (writes that only change the sync flag are skipped, note/sortOrder changes are not missed).
        // debounce(500ms): rapid consecutive writes merge into a single update.
        widgetScope.launch {
            taskRepository.observeAll()
                .map { tasks ->
                    tasks.joinToString("|") { t ->
                        "${t.id}:${t.isDone}:${t.title}:${t.notes}:${t.sortOrder}:" +
                            "${t.dueDate}:${t.categoryNames.sorted().joinToString(",")}:" +
                            "${t.linkedWorkoutId}:${t.linkedProgramId}:" +
                            t.subtasks.joinToString(",") { "${it.id}=${it.isDone}=${it.title}" }
                    }
                }
                .debounce(500)
                .distinctUntilChanged()
                .drop(1) // first emission is the current state; do not update needlessly at launch
                .collect { TaskWidget.requestUpdate(this@PersonalNoteApp) }
        }
    }
}
