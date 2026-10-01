package com.moltrax.personalnoteapp.ui.screen.home

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.ExerciseType
import com.moltrax.personalnoteapp.domain.model.LoggedExercise
import com.moltrax.personalnoteapp.domain.model.LoggedSet
import com.moltrax.personalnoteapp.domain.model.PlannedSet
import com.moltrax.personalnoteapp.domain.model.Priority
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.Workout
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import com.moltrax.personalnoteapp.domain.model.withCompletion
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import com.moltrax.personalnoteapp.domain.util.BirthdayUtils
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.widget.TaskWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

enum class TaskStatus { ALL, ACTIVE, DONE }

data class TaskFilter(
    val status: TaskStatus = TaskStatus.ACTIVE,
    val priority: Priority? = null,
    val category: String? = null,
    val search: String = "",
)

data class HomeUiState(
    val allTasks: List<Task> = emptyList(),
    val filteredTasks: List<Task> = emptyList(),
    // Category names shown in the filter bar (permanent ones + temporary ones with active tasks)
    val categories: List<String> = emptyList(),
    // All categories for the management UI (with persistence info)
    val allCategories: List<Category> = emptyList(),
    val filter: TaskFilter = TaskFilter(),
)

/**
 * Display of a single exercise on the screen opened while completing a workout-linked task. [plannedSets]
 * is the plan the user entered when setting up the workout; it pre-fills the set fields on the
 * completion screen. There is no longer a "target" derived from the previous session.
 */
data class WorkoutCompletionItem(
    val exerciseId: String,
    val exerciseName: String,
    val type: ExerciseType,
    val plannedSets: List<PlannedSet>,
)

/** Request to complete a workout-linked task: that day's workout exercises and plan. */
data class WorkoutCompletionRequest(
    val task: Task,
    val workoutId: String,
    val workoutName: String,
    val items: List<WorkoutCompletionItem>,
    // Previously entered but not yet confirmed draft (exerciseId → set rows). So the user resumes
    // where they left off when the screen is closed and reopened or the app is restarted.
    val draft: Map<String, List<WorkoutDraftSet>> = emptyMap(),
)

/**
 * Draft of a single set row on the completion screen. Fields are stored as text (preserving the
 * user's partial/malformed input). Only the relevant fields are filled per type.
 */
@kotlinx.serialization.Serializable
data class WorkoutDraftSet(
    val reps: String = "",
    val weight: String = "",
    val durationSec: String = "",
    val durationMin: String = "",
    val steps: String = "",
)

/**
 * Snapshot needed to "Undo" a completion. [task] is the task state BEFORE completion
 * (it is restored). For workout-linked completions the saved session ([sessionId]) is also kept, plus
 * the original group ([programGroup]) to roll back the cycle index on a program task.
 */
data class UndoableCompletion(
    val token: Long,
    @StringRes val messageRes: Int,
    val task: Task,
    val sessionId: String? = null,
    val programGroup: WorkoutGroup? = null,
)

/**
 * The ACTUAL sets the user entered for an exercise on the completion screen. Each set is entered
 * separately (reps + weight or duration/steps); the accordion UI fills this list.
 */
data class ActualEntry(
    val exerciseId: String,
    val loggedSets: List<LoggedSet> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepo: TaskRepository,
    private val categoryRepo: CategoryRepository,
    private val syncRepo: SyncRepository,
    private val notifService: NotificationService,
    private val prefs: AppPreferences,
    private val workoutRepo: WorkoutRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Home filter survives process death: restored from SavedStateHandle, written on every change.
    private val _filter = MutableStateFlow(
        TaskFilter(
            status = runCatching {
                TaskStatus.valueOf(savedStateHandle.get<String>(KEY_FILTER_STATUS) ?: TaskStatus.ACTIVE.name)
            }.getOrDefault(TaskStatus.ACTIVE),
            priority = savedStateHandle.get<String>(KEY_FILTER_PRIORITY)?.let {
                runCatching { Priority.valueOf(it) }.getOrNull()
            },
            category = savedStateHandle.get<String>(KEY_FILTER_CATEGORY),
            search = savedStateHandle.get<String>(KEY_FILTER_SEARCH) ?: "",
        )
    )

    val uiState: StateFlow<HomeUiState> = combine(
        taskRepo.observeAll(),
        categoryRepo.observeAll(),
        _filter,
    ) { tasks, categories, filter ->
        val filtered = tasks.filter { task ->
            val statusOk = when (filter.status) {
                TaskStatus.ALL    -> true
                TaskStatus.ACTIVE -> !task.isDone
                TaskStatus.DONE   -> task.isDone
            }
            statusOk &&
            (filter.priority == null || task.priority == filter.priority) &&
            (filter.category == null || task.category == filter.category) &&
            (filter.search.isBlank() || task.title.contains(filter.search, ignoreCase = true))
        }.let { list ->
            // In the "Completed" filter tasks are sorted by completion date (newest on top);
            // otherwise the manual order (sortOrder) is preserved.
            if (filter.status == TaskStatus.DONE)
                list.sortedByDescending { it.completedAt ?: it.updatedAt }
            else list
        }
        // Filter chips: all permanent categories (even when empty) + temporary categories with at
        // least one incomplete task. The latter is derived from tasks, so categories arriving via
        // sync (with no local category record) are visible too.
        val permanentNames = categories.filter { it.isPermanent }.map { it.name }
        val activeNames = tasks.filter { !it.isDone }.mapNotNull { it.category?.takeIf(String::isNotBlank) }
        val chipNames = (permanentNames + activeNames).distinct().sortedBy { it.lowercase() }
        HomeUiState(tasks, filtered, chipNames, categories, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // Birthday celebration: true when today is the user's birthday and it has not been shown yet today.
    private val _showBirthday = MutableStateFlow(false)
    val showBirthday: StateFlow<Boolean> = _showBirthday.asStateFlow()

    /** Age shown in the celebration (the age turned this year); null when it is not a birthday. */
    val birthdayAge = MutableStateFlow<Int?>(null)

    /** Open workout-task completion request (the BottomSheet shows it); null when none. */
    private val _workoutCompletion = MutableStateFlow<WorkoutCompletionRequest?>(null)
    val workoutCompletion: StateFlow<WorkoutCompletionRequest?> = _workoutCompletion.asStateFlow()

    /** Snapshot for undoing the last completion; the "Undo" Snackbar uses it. */
    private val _undo = MutableStateFlow<UndoableCompletion?>(null)
    val undo: StateFlow<UndoableCompletion?> = _undo.asStateFlow()

    /** Monotonic counter for undo tokens (avoids millis collisions). */
    private val undoTokenSeq = AtomicLong(0L)

    /** Triggers a Snackbar when arriving from the widget with an unknown/deleted task id (0 = none). */
    private val taskNotFoundSeq = AtomicLong(0L)
    private val _taskNotFoundTick = MutableStateFlow(0L)
    val taskNotFoundTick: StateFlow<Long> = _taskNotFoundTick.asStateFlow()

    /** Session id of the workout summary/result page to open; cleared once consumed by the screen.
     *  Backed by SavedStateHandle → survives process death, the result screen is preserved. */
    val openSummarySessionId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_PENDING_SUMMARY, null)

    // Draft cache (task id → exerciseId → set rows). Also written to DataStore (persisted across
    // app restarts). The in-memory copy allows fast access and survives the screen closing.
    private val draftJson = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Map<String, List<WorkoutDraftSet>>>>(emptyMap())
    private val draftSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun consumeSummary() { savedStateHandle.remove<String>(KEY_PENDING_SUMMARY) }

    init {
        // On launch first pull-merge-push: avoids overwriting the remote backup while local is empty
        viewModelScope.launch { syncRepo.sync() }
        checkBirthday()
        // Load persisted drafts (if any) into memory; the completion screen can reopen where it left off.
        viewModelScope.launch {
            val raw = prefs.workoutDrafts.first()
            if (!raw.isNullOrBlank()) {
                runCatching {
                    draftSerializer.decodeFromString<Map<String, Map<String, List<WorkoutDraftSet>>>>(raw)
                }.getOrNull()?.let { draftJson.value = it }
            }
        }
    }

    /**
     * Saves the draft on the completion screen (all set rows entered for the task). Keeps it in memory
     * and writes it to DataStore, so the data survives even if the screen is closed or the app restarts.
     */
    fun saveWorkoutDraft(taskId: String, rows: Map<String, List<WorkoutDraftSet>>) {
        val next = draftJson.value.toMutableMap().apply { put(taskId, rows) }
        draftJson.value = next
        viewModelScope.launch { prefs.setWorkoutDrafts(draftSerializer.encodeToString(next)) }
    }

    /** Deletes a task's draft (when the completion is confirmed or the task is deleted). */
    private fun clearWorkoutDraft(taskId: String) {
        if (taskId !in draftJson.value) return
        val next = draftJson.value.toMutableMap().apply { remove(taskId) }
        draftJson.value = next
        viewModelScope.launch {
            prefs.setWorkoutDrafts(if (next.isEmpty()) null else draftSerializer.encodeToString(next))
        }
    }

    /**
     * Checks whether today is a birthday. To show the celebration only once a day, the last shown
     * day is stored in DataStore; if already shown today, it is not opened again.
     */
    private fun checkBirthday() {
        viewModelScope.launch {
            val birthDate = BirthdayUtils.parse(prefs.birthDate.first()) ?: return@launch
            val today = LocalDate.now()
            if (!BirthdayUtils.isBirthday(birthDate, today)) return@launch
            if (prefs.birthdayShownOn.first() == today.toString()) return@launch

            birthdayAge.value = BirthdayUtils.calculateAge(birthDate, today)
            _showBirthday.value = true
            // Mark as "shown" immediately → not shown again today even if the app is reopened
            prefs.setBirthdayShownOn(today.toString())
        }
    }

    fun dismissBirthday() {
        _showBirthday.value = false
    }

    fun updateFilter(f: TaskFilter) {
        _filter.update { f }
        savedStateHandle[KEY_FILTER_STATUS] = f.status.name
        if (f.priority == null) savedStateHandle.remove<String>(KEY_FILTER_PRIORITY)
        else savedStateHandle[KEY_FILTER_PRIORITY] = f.priority.name
        if (f.category == null) savedStateHandle.remove<String>(KEY_FILTER_CATEGORY)
        else savedStateHandle[KEY_FILTER_CATEGORY] = f.category
        savedStateHandle[KEY_FILTER_SEARCH] = f.search
    }

    // --- Permanent category management (Manage Categories UI) ---

    fun addPermanentCategory(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch { categoryRepo.ensureExists(n, isPermanent = true) }
    }

    fun renameCategory(oldName: String, newName: String) {
        val new = newName.trim()
        if (new.isBlank() || new == oldName) return
        viewModelScope.launch {
            categoryRepo.rename(oldName, new)
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            categoryRepo.delete(name)
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
        }
    }

    /**
     * Called when the user reorders the list via drag & drop. [displayedIds] is the NEW order of the
     * currently visible (filtered) tasks. Global positions of hidden tasks are preserved: walk the
     * master ordered list and refill the slots occupied by visible items with the new order. Then new
     * sortOrder values 0..n are assigned to all tasks and only changed ones are written to the database.
     */
    fun reorderTasks(displayedIds: List<String>) {
        viewModelScope.launch {
            val master = taskRepo.getAll()              // sorted by sortOrder ASC (DAO)
            val byId = master.associateBy { it.id }
            val displayedSet = displayedIds.toSet()
            val iter = displayedIds.iterator()
            // Rebuild the master order: put the new order into visible slots, keep their own id for hidden ones.
            // iter.hasNext() guard: if a task is concurrently deleted/added during the drag so master
            // and displayedIds no longer match (e.g. completion from the widget), stay on its own id
            // instead of throwing NoSuchElement.
            val newOrderIds = master.map { if (it.id in displayedSet && iter.hasNext()) iter.next() else it.id }

            val now = System.currentTimeMillis()
            val changed = newOrderIds.mapIndexedNotNull { index, id ->
                val task = byId[id] ?: return@mapIndexedNotNull null
                if (task.sortOrder != index.toLong()) task.copy(sortOrder = index.toLong(), updatedAt = now)
                else null
            }
            if (changed.isEmpty()) return@launch
            changed.forEach { taskRepo.upsert(it) }
            // Refresh the order-vector clock so this ordering is published in the sync merge.
            taskRepo.setTaskOrderTimestamp(now)
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
        }
    }

    /**
     * Called when a task checkbox is tapped. If the task is not yet completed AND linked to a
     * workout/program, open the "actual values" entry (accordion) screen instead of closing it directly.
     * Otherwise (unlinked task or reopening) toggle the state directly.
     */
    fun toggleDone(task: Task) {
        if (!task.isDone && (task.linkedWorkoutId != null || task.linkedProgramId != null)) {
            requestWorkoutCompletion(task)
            return
        }
        viewModelScope.launch { applyToggle(task) }
    }

    private suspend fun applyToggle(task: Task, registerUndo: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!task.isDone) {
            // Completing: recurring tasks roll forward (stay open), normal tasks close
            val result = task.withCompletion(now)
            taskRepo.upsert(result)
            notifService.cancelReminder(task.id)
            if (!result.isDone && prefs.systemAlertsEnabled.first()) {
                notifService.scheduleReminder(result, prefs.reminderMinutes.first())
            }
            // Undo opportunity against accidental completion (unlinked/normal task flow).
            if (registerUndo) {
                _undo.value = UndoableCompletion(
                    token = undoTokenSeq.incrementAndGet(),
                    messageRes = R.string.task_completed,
                    task = task, // pre-completion state
                )
            }
        } else {
            // Reopening
            taskRepo.upsert(task.copy(isDone = false, completedAt = null, updatedAt = now))
        }
        syncRepo.pushToDrive()
        TaskWidget.requestUpdate(context)
    }

    /**
     * Undoes the last completion: restores the task to its old state; for a workout-linked completion
     * it also deletes the saved session and moves the cycle index back on a program task.
     */
    fun undoLastCompletion() {
        val u = _undo.value ?: return
        viewModelScope.launch {
            taskRepo.upsert(u.task)
            notifService.cancelReminder(u.task.id)
            if (!u.task.isDone && prefs.systemAlertsEnabled.first()) {
                notifService.scheduleReminder(u.task, prefs.reminderMinutes.first())
            }
            u.sessionId?.let { workoutRepo.deleteSession(it) }
            u.programGroup?.let { workoutRepo.upsertGroup(it) }
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
            _undo.value = null
        }
    }

    fun clearUndo() { _undo.value = null }

    // --- Workout-linked task completion (actual data + EXP) ---

    /** Resolves that day's workout and group from the workout/program linked to the task. */
    private fun resolveWorkout(task: Task, groups: List<WorkoutGroup>): Pair<WorkoutGroup, Workout>? {
        task.linkedWorkoutId?.let { wid ->
            groups.forEach { g -> g.workouts.find { it.id == wid }?.let { return g to it } }
        }
        task.linkedProgramId?.let { pid ->
            val g = groups.find { it.id == pid } ?: return null
            if (g.workouts.isEmpty()) return null
            val idx = g.currentIndex.coerceIn(0, g.workouts.lastIndex)
            return g to g.workouts[idx]
        }
        return null
    }

    /**
     * Workout-task completion request coming from the widget: resolves the task from the id and (if not
     * completed) opens the set/reps/weight entry screen. If the task is not found, a "not found" event
     * is published (the UI shows a Snackbar); if already completed, it is silently skipped.
     */
    fun openWorkoutCompletion(taskId: String) {
        viewModelScope.launch {
            val task = taskRepo.getById(taskId)
            if (task == null) {
                _taskNotFoundTick.value = taskNotFoundSeq.incrementAndGet()
                return@launch
            }
            if (task.isDone) return@launch
            requestWorkoutCompletion(task)
        }
    }

    /** Opens the completion screen pre-filled with target values. Falls back to a normal completion if the workout cannot be resolved. */
    private fun requestWorkoutCompletion(task: Task) {
        viewModelScope.launch {
            val groups = workoutRepo.getGroups()
            val resolved = resolveWorkout(task, groups)
            if (resolved == null) {
                applyToggle(task) // broken/deleted link → complete normally
                return@launch
            }
            val (_, workout) = resolved
            _workoutCompletion.update {
                WorkoutCompletionRequest(
                    task = task,
                    workoutId = workout.id,
                    workoutName = workout.name,
                    items = workout.exercises.map { ex ->
                        WorkoutCompletionItem(ex.exerciseId, ex.exerciseName, ex.type, ex.plannedSets)
                    },
                    // Open with a previously entered draft if any (so the user resumes where they left off).
                    draft = draftJson.value[task.id].orEmpty(),
                )
            }
        }
    }

    /**
     * Opens the summary page of the latest workout session for a completed workout task. If the task
     * is linked and has a saved session, [openSummarySessionId] is updated; otherwise no-op.
     */
    fun requestSummaryForTask(taskId: String) {
        viewModelScope.launch {
            workoutRepo.getLatestSessionForTask(taskId)?.let { savedStateHandle[KEY_PENDING_SUMMARY] = it.id }
        }
    }

    fun cancelWorkoutCompletion() = _workoutCompletion.update { null }

    /**
     * When the user enters the actual sets (on the accordion screen) and confirms: builds a
     * WorkoutSession from the session and persists it, advances the cycle by one day on a program
     * task, and marks the task completed. Offers undo via Snackbar against accidental completion.
     */
    fun submitWorkoutCompletion(actuals: List<ActualEntry>) {
        val request = _workoutCompletion.value ?: return
        viewModelScope.launch {
            val byId = actuals.associateBy { it.exerciseId }
            val groups = workoutRepo.getGroups()
            val resolved = resolveWorkout(request.task, groups)
            val workout = resolved?.second

            val loggedExercises = request.items.map { item ->
                // The accordion screen produces each exercise's sets directly as LoggedSet;
                // empty sets carrying no meaningful data are filtered out.
                val sets = (byId[item.exerciseId]?.loggedSets ?: emptyList()).filter { it.isMeaningful() }
                LoggedExercise(item.exerciseId, item.exerciseName, sets, item.type)
            }

            val now = System.currentTimeMillis()
            val session = WorkoutSession(
                workoutId = request.workoutId,
                workoutName = request.workoutName,
                startedAt = now,
                completedAt = now,
                loggedExercises = loggedExercises,
                taskId = request.task.id,
            )
            workoutRepo.saveSession(session)

            // Advance the cycle by one day on a program task (rotation). Keep the original group for undo.
            var originalGroup: WorkoutGroup? = null
            if (request.task.linkedProgramId != null && resolved != null && workout != null) {
                val group = resolved.first
                if (group.workouts.size > 1) {
                    originalGroup = group
                    val nextIndex = (group.currentIndex + 1) % group.workouts.size
                    workoutRepo.upsertGroup(group.copy(currentIndex = nextIndex))
                }
            }

            // Complete the task; build the undo snapshot here with session/group info.
            applyToggle(request.task, registerUndo = false)
            _undo.value = UndoableCompletion(
                token = undoTokenSeq.incrementAndGet(),
                messageRes = R.string.workout_completed,
                task = request.task,
                sessionId = session.id,
                programGroup = originalGroup,
            )
            // Confirmed: clear the draft and open the summary/result page of the completed workout.
            clearWorkoutDraft(request.task.id)
            _workoutCompletion.update { null }
            savedStateHandle[KEY_PENDING_SUMMARY] = session.id
        }
    }

    fun deleteTask(id: String) {
        clearWorkoutDraft(id)
        viewModelScope.launch {
            notifService.cancelReminder(id)
            taskRepo.delete(id)
            // Automatically clean up temporary categories left orphaned when the task is deleted
            categoryRepo.cleanupTemporary()
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
        }
    }

    companion object {
        private const val KEY_FILTER_STATUS = "home_filter_status"
        private const val KEY_FILTER_PRIORITY = "home_filter_priority"
        private const val KEY_FILTER_CATEGORY = "home_filter_category"
        private const val KEY_FILTER_SEARCH = "home_filter_search"
        private const val KEY_PENDING_SUMMARY = "pending_summary_id"
    }
}
