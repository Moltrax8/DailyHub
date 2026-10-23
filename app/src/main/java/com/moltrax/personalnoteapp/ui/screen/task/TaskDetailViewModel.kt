package com.moltrax.personalnoteapp.ui.screen.task

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.Priority
import com.moltrax.personalnoteapp.domain.model.RecurrenceType
import com.moltrax.personalnoteapp.domain.model.SubTask
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.widget.TaskWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

/**
 * Removes one subtask by id for the delete-confirm flow (the screen confirms,
 * then calls [TaskDetailViewModel.removeSubtask]). Pure list op — unit-tested.
 */
internal fun List<SubTask>.withoutSubtask(id: String): List<SubTask> =
    filterNot { it.id == id }

data class TaskDetailState(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String = "",
    val dueDate: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val isRecurring: Boolean = false,
    val intervalDays: Int? = null,
    val recurrenceType: RecurrenceType = RecurrenceType.DAILY,
    val recurrenceDaysOfWeek: List<Int> = emptyList(),
    val focusDurationSeconds: Int = 1500,
    val categories: Set<String> = emptySet(),
    val subtasks: List<SubTask> = emptyList(),
    val isNew: Boolean = true,
    val isSaving: Boolean = false,
    val intervalError: Boolean = false,
    val weeklyError: Boolean = false,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepo: TaskRepository,
    private val categoryRepo: CategoryRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun restoreFromHandle(): TaskDetailState? {
        if (!savedStateHandle.contains(KEY_TITLE) && !savedStateHandle.contains(KEY_ID)) return null
        return TaskDetailState(
            id = savedStateHandle.get<String>(KEY_ID) ?: return null,
            title = savedStateHandle.get<String>(KEY_TITLE).orEmpty(),
            notes = savedStateHandle.get<String>(KEY_NOTES).orEmpty(),
            dueDate = savedStateHandle.get<Long>(KEY_DUE),
            priority = savedStateHandle.get<String>(KEY_PRIORITY)?.let {
                runCatching { Priority.valueOf(it) }.getOrNull()
            } ?: Priority.MEDIUM,
            isRecurring = savedStateHandle.get<Boolean>(KEY_RECURRING) ?: false,
            intervalDays = savedStateHandle.get<Int>(KEY_INTERVAL),
            recurrenceType = savedStateHandle.get<String>(KEY_REC_TYPE)?.let {
                runCatching { RecurrenceType.valueOf(it) }.getOrNull()
            } ?: RecurrenceType.DAILY,
            recurrenceDaysOfWeek = savedStateHandle.get<IntArray>(KEY_DAYS)?.toList() ?: emptyList(),
            focusDurationSeconds = savedStateHandle.get<Int>(KEY_FOCUS) ?: 1500,
            categories = savedStateHandle.get<Array<String>>(KEY_CATEGORIES)?.toSet().orEmpty(),
            subtasks = savedStateHandle.get<String>(KEY_SUBTASKS)?.let {
                runCatching { json.decodeFromString<List<SubTask>>(it) }.getOrDefault(emptyList())
            } ?: emptyList(),
            isNew = savedStateHandle.get<Boolean>(KEY_IS_NEW) ?: true,
        )
    }

    private fun persist(s: TaskDetailState) {
        savedStateHandle[KEY_ID] = s.id
        savedStateHandle[KEY_TITLE] = s.title
        savedStateHandle[KEY_NOTES] = s.notes
        if (s.dueDate == null) savedStateHandle.remove<Long>(KEY_DUE) else savedStateHandle[KEY_DUE] = s.dueDate
        savedStateHandle[KEY_PRIORITY] = s.priority.name
        savedStateHandle[KEY_RECURRING] = s.isRecurring
        if (s.intervalDays == null) savedStateHandle.remove<Int>(KEY_INTERVAL) else savedStateHandle[KEY_INTERVAL] = s.intervalDays
        savedStateHandle[KEY_REC_TYPE] = s.recurrenceType.name
        savedStateHandle[KEY_DAYS] = s.recurrenceDaysOfWeek.toIntArray()
        savedStateHandle[KEY_FOCUS] = s.focusDurationSeconds
        savedStateHandle[KEY_CATEGORIES] = s.categories.toTypedArray()
        savedStateHandle[KEY_SUBTASKS] = json.encodeToString(s.subtasks)
        savedStateHandle[KEY_IS_NEW] = s.isNew
    }

    private val _state = MutableStateFlow(restoreFromHandle() ?: TaskDetailState())
    val state: StateFlow<TaskDetailState> = _state.asStateFlow()

    val categories: StateFlow<List<Category>> = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleCategory(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        _state.update {
            it.copy(categories = if (it.categories.any { c -> c.equals(n, ignoreCase = true) }) {
                it.categories.filterNot { c -> c.equals(n, ignoreCase = true) }.toSet()
            } else {
                it.categories + n
            })
        }
        persist(_state.value)
    }

    fun clearCategories() {
        _state.update { it.copy(categories = emptySet()) }
        persist(_state.value)
    }

    fun createCategory(name: String, isPermanent: Boolean) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch {
            categoryRepo.ensureExists(n, isPermanent)
            _state.update { s ->
                s.copy(categories = if (s.categories.any { it.equals(n, ignoreCase = true) }) s.categories else s.categories + n)
            }
            persist(_state.value)
        }
    }

    fun load(taskId: String) {
        if (savedStateHandle.contains(KEY_ID) || savedStateHandle.contains(KEY_TITLE)) return
        if (taskId == "new") return
        viewModelScope.launch {
            taskRepo.getById(taskId)?.let { t ->
                _state.update {
                    it.copy(
                        id = t.id, title = t.title, notes = t.notes ?: "",
                        dueDate = t.dueDate, priority = t.priority,
                        isRecurring = t.isRecurring, intervalDays = t.intervalDays,
                        recurrenceType = t.recurrenceType
                            ?: if (t.intervalDays != null) RecurrenceType.INTERVAL else RecurrenceType.DAILY,
                        recurrenceDaysOfWeek = t.recurrenceDaysOfWeek,
                        focusDurationSeconds = t.focusDurationSeconds,
                        categories = t.categoryNames.ifEmpty {
                            t.category?.takeIf { it.isNotBlank() }?.let(::setOf).orEmpty()
                        },
                        subtasks = t.subtasks,
                        isNew = false,
                    )
                }
                persist(_state.value)
            }
        }
    }

    fun update(block: TaskDetailState.() -> TaskDetailState) {
        _state.update {
            block(it).copy(intervalError = false, weeklyError = false)
        }
        persist(_state.value.copy(intervalError = false, weeklyError = false))
    }

    fun addSubtask(title: String) {
        val t = title.trim()
        if (t.isBlank()) return
        _state.update { it.copy(subtasks = it.subtasks + SubTask(title = t)) }
        persist(_state.value)
    }

    fun toggleSubtask(id: String) {
        _state.update { s ->
            s.copy(subtasks = s.subtasks.map { if (it.id == id) it.copy(isDone = !it.isDone) else it })
        }
        persist(_state.value)
    }

    fun removeSubtask(id: String) {
        _state.update { s -> s.copy(subtasks = s.subtasks.withoutSubtask(id)) }
        persist(_state.value)
    }

    fun toggleRecurrenceDay(dayIso: Int) {
        _state.update { s ->
            val days = if (dayIso in s.recurrenceDaysOfWeek) s.recurrenceDaysOfWeek - dayIso
            else s.recurrenceDaysOfWeek + dayIso
            s.copy(recurrenceDaysOfWeek = days.sorted(), weeklyError = false)
        }
        persist(_state.value)
    }

    private fun validateRecurrence(s: TaskDetailState): Boolean {
        if (!s.isRecurring) return true
        var ok = true
        var intervalErr = false
        var weeklyErr = false
        if (s.recurrenceType == RecurrenceType.INTERVAL) {
            val n = s.intervalDays
            if (n == null || n <= 0) { intervalErr = true; ok = false }
        }
        if (s.recurrenceType == RecurrenceType.WEEKLY) {
            if (s.recurrenceDaysOfWeek.isEmpty()) { weeklyErr = true; ok = false }
        }
        if (!ok) _state.update { it.copy(intervalError = intervalErr, weeklyError = weeklyErr) }
        return ok
    }

    fun save(onDone: () -> Unit) {
        if (_state.value.title.isBlank() || _state.value.isSaving) return
        if (!validateRecurrence(_state.value)) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val s = _state.value
            val now = System.currentTimeMillis()
            val existing = if (s.isNew) null else taskRepo.getById(s.id)
            val sortOrder = existing?.sortOrder
                ?: ((taskRepo.getAll().minOfOrNull { it.sortOrder } ?: 0L) - 1L)
            val task = Task(
                id = s.id, title = s.title.trim(), notes = s.notes.takeIf { it.isNotBlank() },
                dueDate = s.dueDate, priority = s.priority, isRecurring = s.isRecurring,
                intervalDays = if (s.isRecurring && s.recurrenceType == RecurrenceType.INTERVAL) s.intervalDays else null,
                recurrenceType = if (s.isRecurring) s.recurrenceType else null,
                recurrenceDaysOfWeek = if (s.isRecurring && s.recurrenceType == RecurrenceType.WEEKLY) s.recurrenceDaysOfWeek else emptyList(),
                focusDurationSeconds = s.focusDurationSeconds,
                categoryNames = s.categories,
                subtasks = s.subtasks,
                createdAt = if (s.isNew) now else existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = sortOrder,
                isDone = existing?.isDone ?: false,
                completedAt = existing?.completedAt,
                priorityRaw = existing?.priorityRaw,
            )
            taskRepo.upsert(task)
            s.categories.forEach { categoryRepo.ensureExists(it) }
            categoryRepo.cleanupTemporary()
            TaskWidget.requestUpdate(context)
            _state.update { it.copy(isSaving = false) }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (!_state.value.isNew) taskRepo.delete(_state.value.id)
            TaskWidget.requestUpdate(context)
            onDone()
        }
    }

    companion object {
        private const val KEY_ID = "task_id"
        private const val KEY_TITLE = "task_title"
        private const val KEY_NOTES = "task_notes"
        private const val KEY_DUE = "task_due"
        private const val KEY_PRIORITY = "task_priority"
        private const val KEY_RECURRING = "task_recurring"
        private const val KEY_INTERVAL = "task_interval"
        private const val KEY_REC_TYPE = "task_rec_type"
        private const val KEY_DAYS = "task_days"
        private const val KEY_FOCUS = "task_focus"
        private const val KEY_CATEGORIES = "task_categories"
        private const val KEY_SUBTASKS = "task_subtasks"
        private const val KEY_IS_NEW = "task_is_new"
    }
}
