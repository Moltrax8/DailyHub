package com.moltrax.personalnoteapp.ui.screen.task

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.Priority
import com.moltrax.personalnoteapp.domain.model.RecurrenceType
import com.moltrax.personalnoteapp.domain.model.SubTask
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.WorkoutGroup
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.widget.TaskWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

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
    val category: String = "",
    val subtasks: List<SubTask> = emptyList(),
    val linkedWorkoutId: String? = null,
    val linkedProgramId: String? = null,
    val programStartIndex: Int = 0,
    val isNew: Boolean = true,
    val isSaving: Boolean = false,
    // Tekrar doğrulama hataları (kaydetmeden önce, satır içi gösterilir).
    val intervalError: Boolean = false,
    val weeklyError: Boolean = false,
)

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepo: TaskRepository,
    private val categoryRepo: CategoryRepository,
    private val workoutRepo: WorkoutRepository,
    private val syncRepo: SyncRepository,
    private val notifService: NotificationService,
    private val prefs: AppPreferences,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private fun restoreFromHandle(): TaskDetailState? {
        // Kayıtlı düzenleme yoksa null (taze açılış) → repo'dan yüklenir.
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
            category = savedStateHandle.get<String>(KEY_CATEGORY).orEmpty(),
            subtasks = savedStateHandle.get<String>(KEY_SUBTASKS)?.let {
                runCatching { json.decodeFromString<List<SubTask>>(it) }.getOrDefault(emptyList())
            } ?: emptyList(),
            linkedWorkoutId = savedStateHandle.get<String>(KEY_LINK_W),
            linkedProgramId = savedStateHandle.get<String>(KEY_LINK_P),
            programStartIndex = savedStateHandle.get<Int>(KEY_PROG_IDX) ?: 0,
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
        savedStateHandle[KEY_CATEGORY] = s.category
        savedStateHandle[KEY_SUBTASKS] = json.encodeToString(s.subtasks)
        if (s.linkedWorkoutId == null) savedStateHandle.remove<String>(KEY_LINK_W) else savedStateHandle[KEY_LINK_W] = s.linkedWorkoutId
        if (s.linkedProgramId == null) savedStateHandle.remove<String>(KEY_LINK_P) else savedStateHandle[KEY_LINK_P] = s.linkedProgramId
        savedStateHandle[KEY_PROG_IDX] = s.programStartIndex
        savedStateHandle[KEY_IS_NEW] = s.isNew
    }

    private val _state = MutableStateFlow(restoreFromHandle() ?: TaskDetailState())
    val state: StateFlow<TaskDetailState> = _state.asStateFlow()

    val workoutGroups: StateFlow<List<WorkoutGroup>> = workoutRepo.observeGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mevcut bir kategoriyi seçer ya da seçimi kaldırır (null). */
    fun selectCategory(name: String?) {
        _state.update { it.copy(category = name?.trim().orEmpty()) }
        persist(_state.value)
    }

    /** Yeni kategori oluşturur (kalıcı olabilir) ve görevde seçili hale getirir. */
    fun createCategory(name: String, isPermanent: Boolean) {
        val n = name.trim()
        if (n.isBlank()) return
        viewModelScope.launch {
            categoryRepo.ensureExists(n, isPermanent)
            _state.update { it.copy(category = n) }
            persist(_state.value)
        }
    }

    fun load(taskId: String) {
        // Process-death sonrası geri yüklenen düzenleme varsa repo ile ezme.
        if (savedStateHandle.contains(KEY_ID) || savedStateHandle.contains(KEY_TITLE)) return
        if (taskId == "new") return
        viewModelScope.launch {
            taskRepo.getById(taskId)?.let { t ->
                _state.update {
                    it.copy(
                        id = t.id, title = t.title, notes = t.notes ?: "",
                        dueDate = t.dueDate, priority = t.priority,
                        isRecurring = t.isRecurring, intervalDays = t.intervalDays,
                        // Eski (recurrenceType=null) ama intervalDays'li görevler INTERVAL kabul edilir.
                        recurrenceType = t.recurrenceType
                            ?: if (t.intervalDays != null) RecurrenceType.INTERVAL else RecurrenceType.DAILY,
                        recurrenceDaysOfWeek = t.recurrenceDaysOfWeek,
                        focusDurationSeconds = t.focusDurationSeconds,
                        category = t.category ?: "",
                        subtasks = t.subtasks,
                        linkedWorkoutId = t.linkedWorkoutId,
                        linkedProgramId = t.linkedProgramId,
                        programStartIndex = t.programStartIndex,
                        isNew = false,
                    )
                }
                persist(_state.value)
            }
        }
    }

    fun update(block: TaskDetailState.() -> TaskDetailState) {
        _state.update {
            // Düzenlemede eski satır-içi tekrar hatalarını temizle; save() yeniden doğrular.
            block(it).copy(intervalError = false, weeklyError = false)
        }
        persist(_state.value.copy(intervalError = false, weeklyError = false))
    }

    // --- Alt görev (checklist) düzenleme ---

    /** Yeni bir alt görev ekler (başlık boşsa yok sayılır). */
    fun addSubtask(title: String) {
        val t = title.trim()
        if (t.isBlank()) return
        _state.update { it.copy(subtasks = it.subtasks + SubTask(title = t)) }
        persist(_state.value)
    }

    /** Bir alt görevin tamamlanma durumunu değiştirir. */
    fun toggleSubtask(id: String) {
        _state.update { s ->
            s.copy(subtasks = s.subtasks.map { if (it.id == id) it.copy(isDone = !it.isDone) else it })
        }
        persist(_state.value)
    }

    /** Bir alt görevi siler. */
    fun removeSubtask(id: String) {
        _state.update { s ->
            s.copy(subtasks = s.subtasks.filterNot { it.id == id })
        }
        persist(_state.value)
    }

    /** Haftalık tekrarda bir günü (ISO 1..7) ekler/çıkarır. */
    fun toggleRecurrenceDay(dayIso: Int) {
        _state.update { s ->
            val days = if (dayIso in s.recurrenceDaysOfWeek) s.recurrenceDaysOfWeek - dayIso
                       else s.recurrenceDaysOfWeek + dayIso
            s.copy(recurrenceDaysOfWeek = days.sorted(), weeklyError = false)
        }
        persist(_state.value)
    }

    /** Kaydetmeden önce tekrar biçimini doğrular; geçersizse satır-içi hata bayrağı kurup false döner. */
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
        // INTERVAL boş/0/negatif ya da WEEKLY günsüz ise kaydetme (nextRecurrenceDue kalıcı kapatırdı).
        if (!validateRecurrence(_state.value)) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val s = _state.value
            val now = System.currentTimeMillis()
            val existing = if (s.isNew) null else taskRepo.getById(s.id)
            // Yeni görev listenin başına gelsin: mevcut en küçük sortOrder'dan bir eksiği.
            // Düzenlemede mevcut sıralama korunur.
            val sortOrder = existing?.sortOrder
                ?: ((taskRepo.getAll().minOfOrNull { it.sortOrder } ?: 0L) - 1L)
            val task = Task(
                id = s.id, title = s.title.trim(), notes = s.notes.takeIf { it.isNotBlank() },
                dueDate = s.dueDate, priority = s.priority, isRecurring = s.isRecurring,
                // Tekrar kapalıysa biçim/aralık/gün bilgisini sıfırla; INTERVAL'da gün aralığı şart.
                intervalDays = if (s.isRecurring && s.recurrenceType == RecurrenceType.INTERVAL) s.intervalDays else null,
                recurrenceType = if (s.isRecurring) s.recurrenceType else null,
                recurrenceDaysOfWeek = if (s.isRecurring && s.recurrenceType == RecurrenceType.WEEKLY) s.recurrenceDaysOfWeek else emptyList(),
                focusDurationSeconds = s.focusDurationSeconds,
                category = s.category.takeIf { it.isNotBlank() },
                subtasks = s.subtasks,
                // Tek antrenman ile tüm program bağı birbirini dışlar.
                linkedWorkoutId = if (s.linkedProgramId != null) null else s.linkedWorkoutId,
                linkedProgramId = s.linkedProgramId,
                programStartIndex = s.programStartIndex,
                createdAt = if (s.isNew) now else existing?.createdAt ?: now,
                updatedAt = now,
                sortOrder = sortOrder,
            )
            taskRepo.upsert(task)
            // Program bağında döngü, seçilen başlangıç gününden başlasın: grubun currentIndex'ini ayarla.
            task.linkedProgramId?.let { pid ->
                workoutRepo.getGroups().find { it.id == pid }?.let { group ->
                    val start = task.programStartIndex.coerceIn(0, group.workouts.lastIndex.coerceAtLeast(0))
                    if (group.currentIndex != start) workoutRepo.upsertGroup(group.copy(currentIndex = start))
                }
            }
            // Kategori yaşam döngüsü: seçili kategoriyi kayıt altına al (yoksa geçici oluşur),
            // ardından kategori değişmişse boşa çıkan eski geçici kategorileri temizle.
            task.category?.let { categoryRepo.ensureExists(it) }
            categoryRepo.cleanupTemporary()
            notifService.cancelReminder(task.id)
            if (task.dueDate != null && prefs.systemAlertsEnabled.first()) {
                notifService.scheduleReminder(task, prefs.reminderMinutes.first())
            }
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
            _state.update { it.copy(isSaving = false) }
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
        private const val KEY_CATEGORY = "task_category"
        private const val KEY_SUBTASKS = "task_subtasks"
        private const val KEY_LINK_W = "task_link_w"
        private const val KEY_LINK_P = "task_link_p"
        private const val KEY_PROG_IDX = "task_prog_idx"
        private const val KEY_IS_NEW = "task_is_new"
    }
}
