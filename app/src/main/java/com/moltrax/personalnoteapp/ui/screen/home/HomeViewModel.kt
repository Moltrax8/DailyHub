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
    // Filtre çubuğunda gösterilecek kategori adları (kalıcılar + aktif görevli geçiciler)
    val categories: List<String> = emptyList(),
    // Yönetim arayüzü için tüm kategoriler (kalıcılık bilgisiyle)
    val allCategories: List<Category> = emptyList(),
    val filter: TaskFilter = TaskFilter(),
)

/**
 * Spora linkli bir görev tamamlanırken açılan ekranda tek bir hareketin gösterimi. [plannedSets]
 * kullanıcının antrenmanı kurarken girdiği plandır; tamamlama ekranındaki set alanlarını ön-doldurmak
 * için kullanılır. Artık önceki seanstan türetilen bir "hedef" yoktur.
 */
data class WorkoutCompletionItem(
    val exerciseId: String,
    val exerciseName: String,
    val type: ExerciseType,
    val plannedSets: List<PlannedSet>,
)

/** Spora linkli görevi tamamlama isteği: o günkü antrenmanın hareketleri ve planı. */
data class WorkoutCompletionRequest(
    val task: Task,
    val workoutId: String,
    val workoutName: String,
    val items: List<WorkoutCompletionItem>,
    // Daha önce girilmiş ama henüz onaylanmamış taslak (exerciseId → set satırları). Ekran kapanıp
    // tekrar açıldığında ya da uygulama yeniden başladığında kullanıcı kaldığı yerden devam etsin diye.
    val draft: Map<String, List<WorkoutDraftSet>> = emptyMap(),
)

/**
 * Tamamlama ekranındaki tek bir set satırının taslağı. Alanlar metin olarak saklanır (kullanıcının
 * yarım/biçimsiz girişi dahil korunur). Tipe göre yalnızca ilgili alanlar doldurulur.
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
 * Bir tamamlamayı "Geri Al" için gereken anlık görüntü. [task] tamamlamadan ÖNCEKİ görev durumudur
 * (geri yüklenir). Spora linkli tamamlamalarda ayrıca kaydedilen seans ([sessionId]) ve programlı
 * görevde döngü indeksini geri almak için orijinal grup ([programGroup]) tutulur.
 */
data class UndoableCompletion(
    val token: Long,
    @StringRes val messageRes: Int,
    val task: Task,
    val sessionId: String? = null,
    val programGroup: WorkoutGroup? = null,
)

/**
 * Tamamlama ekranında kullanıcının bir hareket için girdiği GERÇEKLEŞEN setler. Her set ayrı ayrı
 * (tekrar + ağırlık veya süre/adım) girilir; akordeon arayüzü bu listeyi doldurur.
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

    // Home filtresi process-death'e dayanıklı: SavedStateHandle'dan geri yüklenir, her değişimde yazılır.
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
            // "Tamamlananlar" filtresinde görevler tamamlanma tarihine göre (en yeni üstte) sıralanır;
            // diğer durumlarda manuel sıralama (sortOrder) korunur.
            if (filter.status == TaskStatus.DONE)
                list.sortedByDescending { it.completedAt ?: it.updatedAt }
            else list
        }
        // Filtre çipleri: tüm kalıcı kategoriler (boş olsalar bile) + en az bir tamamlanmamış
        // görevi olan geçici kategoriler. İkincisi görevlerden türetilir; böylece sync'le gelen
        // (yerel kategori kaydı olmayan) kategoriler de görünür.
        val permanentNames = categories.filter { it.isPermanent }.map { it.name }
        val activeNames = tasks.filter { !it.isDone }.mapNotNull { it.category?.takeIf(String::isNotBlank) }
        val chipNames = (permanentNames + activeNames).distinct().sortedBy { it.lowercase() }
        HomeUiState(tasks, filtered, chipNames, categories, filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // Doğum günü kutlaması: bugün kullanıcının doğum günüyse ve bugün henüz gösterilmediyse true olur.
    private val _showBirthday = MutableStateFlow(false)
    val showBirthday: StateFlow<Boolean> = _showBirthday.asStateFlow()

    /** Kutlamada gösterilecek yaş (bu yıl dolan yaş); doğum günü değilse null. */
    val birthdayAge = MutableStateFlow<Int?>(null)

    /** Açık olan spor görevi tamamlama isteği (BottomSheet bunu gösterir); yoksa null. */
    private val _workoutCompletion = MutableStateFlow<WorkoutCompletionRequest?>(null)
    val workoutCompletion: StateFlow<WorkoutCompletionRequest?> = _workoutCompletion.asStateFlow()

    /** Son tamamlamayı geri almak için anlık görüntü; Snackbar "Geri Al" bunu kullanır. */
    private val _undo = MutableStateFlow<UndoableCompletion?>(null)
    val undo: StateFlow<UndoableCompletion?> = _undo.asStateFlow()

    /** Geri-al jetonları için monoton sayaç (millis çakışmasını önler). */
    private val undoTokenSeq = AtomicLong(0L)

    /** Bilinmeyen/silinmiş görev id'siyle widget'tan gelindiğinde Snackbar tetikler (0 = yok). */
    private val taskNotFoundSeq = AtomicLong(0L)
    private val _taskNotFoundTick = MutableStateFlow(0L)
    val taskNotFoundTick: StateFlow<Long> = _taskNotFoundTick.asStateFlow()

    /** Açılması gereken antrenman özet/sonuç sayfasının seans id'si; ekran tüketince temizlenir.
     *  SavedStateHandle destekli → process death'te kaybolmaz, sonuç ekranı korunur. */
    val openSummarySessionId: StateFlow<String?> =
        savedStateHandle.getStateFlow(KEY_PENDING_SUMMARY, null)

    // Taslak önbelleği (görev id → exerciseId → set satırları). DataStore'a da yazılır (uygulama
    // yeniden başlasa bile kalıcı). Bellek içi kopya hızlı erişim ve ekran kapanmasına dayanıklılık sağlar.
    private val draftJson = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Map<String, List<WorkoutDraftSet>>>>(emptyMap())
    private val draftSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    fun consumeSummary() { savedStateHandle.remove<String>(KEY_PENDING_SUMMARY) }

    init {
        // Açılışta önce çek-birleştir-gönder: yerel boşken uzaktaki yedeği ezmeyi önler
        viewModelScope.launch { syncRepo.sync() }
        checkBirthday()
        // Kalıcı taslakları (varsa) belleğe yükle; tamamlama ekranı kaldığı yerden açılabilsin.
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
     * Tamamlama ekranındaki taslağı (görev için girilen tüm set satırları) kaydeder. Bellekte tutar
     * ve DataStore'a yazar; böylece ekran kapansa veya uygulama yeniden başlasa bile veri korunur.
     */
    fun saveWorkoutDraft(taskId: String, rows: Map<String, List<WorkoutDraftSet>>) {
        val next = draftJson.value.toMutableMap().apply { put(taskId, rows) }
        draftJson.value = next
        viewModelScope.launch { prefs.setWorkoutDrafts(draftSerializer.encodeToString(next)) }
    }

    /** Bir görevin taslağını siler (tamamlama onaylandığında veya görev silindiğinde). */
    private fun clearWorkoutDraft(taskId: String) {
        if (taskId !in draftJson.value) return
        val next = draftJson.value.toMutableMap().apply { remove(taskId) }
        draftJson.value = next
        viewModelScope.launch {
            prefs.setWorkoutDrafts(if (next.isEmpty()) null else draftSerializer.encodeToString(next))
        }
    }

    /**
     * Bugün doğum günü mü kontrol eder. Kutlamayı günde yalnızca bir kez göstermek için
     * en son gösterilen günü DataStore'da saklar; bugün zaten gösterildiyse tekrar açmaz.
     */
    private fun checkBirthday() {
        viewModelScope.launch {
            val birthDate = BirthdayUtils.parse(prefs.birthDate.first()) ?: return@launch
            val today = LocalDate.now()
            if (!BirthdayUtils.isBirthday(birthDate, today)) return@launch
            if (prefs.birthdayShownOn.first() == today.toString()) return@launch

            birthdayAge.value = BirthdayUtils.calculateAge(birthDate, today)
            _showBirthday.value = true
            // Hemen "gösterildi" olarak işaretle → uygulama yeniden açılsa bile bugün tekrar çıkmaz
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

    // --- Kalıcı kategori yönetimi (Kategorileri Yönet arayüzü) ---

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
     * Kullanıcı listeyi sürükle-bırakla yeniden sıraladığında çağrılır. [displayedIds] o an
     * ekranda görünen (filtrelenmiş) görevlerin YENİ sırasıdır. Filtre nedeniyle gizli olan
     * görevlerin global konumları korunur: ana sıralı liste üzerinde yürünür ve görünür
     * öğelerin işgal ettiği yuvalar yeni sıraya göre yeniden doldurulur. Ardından tüm görevlere
     * 0..n aralığında yeni sortOrder atanır ve yalnızca değişenler veritabanına yazılır.
     */
    fun reorderTasks(displayedIds: List<String>) {
        viewModelScope.launch {
            val master = taskRepo.getAll()              // sortOrder ASC sıralı (DAO)
            val byId = master.associateBy { it.id }
            val displayedSet = displayedIds.toSet()
            val iter = displayedIds.iterator()
            // Ana sırayı yeniden kur: görünür yuvalara yeni sırayı, gizlilere kendi id'sini koy.
            // iter.hasNext() koruması: sürükleme sırasında bir görev eşzamanlı silinip/eklenip master
            // ile displayedIds arasındaki eşleşme bozulursa (ör. widget'tan tamamlama) NoSuchElement
            // fırlatmak yerine öğe kendi id'sinde kalır.
            val newOrderIds = master.map { if (it.id in displayedSet && iter.hasNext()) iter.next() else it.id }

            val now = System.currentTimeMillis()
            val changed = newOrderIds.mapIndexedNotNull { index, id ->
                val task = byId[id] ?: return@mapIndexedNotNull null
                if (task.sortOrder != index.toLong()) task.copy(sortOrder = index.toLong(), updatedAt = now)
                else null
            }
            if (changed.isEmpty()) return@launch
            changed.forEach { taskRepo.upsert(it) }
            // Sıra vektörü saatini tazele ki bu sıralama sync birleştirmesinde yayımlansın.
            taskRepo.setTaskOrderTimestamp(now)
            syncRepo.pushToDrive()
            TaskWidget.requestUpdate(context)
        }
    }

    /**
     * Görev tikine basıldığında çağrılır. Görev henüz tamamlanmamış VE bir antrenmana/programa
     * linkliyse direkt kapatmak yerine "gerçekleşen değer" giriş (akordeon) ekranını açar.
     * Aksi halde (linksiz görev ya da tekrar açma) doğrudan durum değiştirir.
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
            // Tamamlanıyor: tekrarlayan görev ileri sarılır (açık kalır), normal görev kapanır
            val result = task.withCompletion(now)
            taskRepo.upsert(result)
            notifService.cancelReminder(task.id)
            if (!result.isDone && prefs.systemAlertsEnabled.first()) {
                notifService.scheduleReminder(result, prefs.reminderMinutes.first())
            }
            // Yanlışlıkla tamamlamaya karşı geri al imkânı (linksiz/normal görev akışı).
            if (registerUndo) {
                _undo.value = UndoableCompletion(
                    token = undoTokenSeq.incrementAndGet(),
                    messageRes = R.string.task_completed,
                    task = task, // tamamlamadan önceki hâl
                )
            }
        } else {
            // Tekrar açılıyor
            taskRepo.upsert(task.copy(isDone = false, completedAt = null, updatedAt = now))
        }
        syncRepo.pushToDrive()
        TaskWidget.requestUpdate(context)
    }

    /**
     * Son tamamlamayı geri alır: görevi eski hâline döndürür; spora linkli tamamlamada ayrıca
     * kaydedilen seansı siler ve programlı görevde döngü indeksini eski konumuna alır.
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

    // --- Spora linkli görev tamamlama (gerçekleşen veri + EXP) ---

    /** Göreve linkli antrenman/programdan o günkü antrenmanı ve grubunu çözer. */
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
     * Widget'tan gelen spor görevi tamamlama isteği: id'den görevi çözer ve (tamamlanmamışsa)
     * set/tekrar/ağırlık giriş ekranını açar. Görev bulunamazsa "bulunamadı" olayı yayınlanır
     * (arayüz Snackbar gösterir); zaten tamamlanmışsa sessizce geçilir.
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

    /** Tamamlama ekranını hedef değerlerle doldurup açar. Antrenman çözülemezse normal tamamlar. */
    private fun requestWorkoutCompletion(task: Task) {
        viewModelScope.launch {
            val groups = workoutRepo.getGroups()
            val resolved = resolveWorkout(task, groups)
            if (resolved == null) {
                applyToggle(task) // link bozuk/silinmiş → normal tamamla
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
                    // Önceden girilmiş taslak varsa onunla aç (kullanıcı kaldığı yerden devam etsin).
                    draft = draftJson.value[task.id].orEmpty(),
                )
            }
        }
    }

    /**
     * Tamamlanmış bir spor görevine ait en son antrenman seansının özet sayfasını açar. Görev
     * linkliyse ve kayıtlı bir seans varsa [openSummarySessionId] güncellenir; aksi halde no-op.
     */
    fun requestSummaryForTask(taskId: String) {
        viewModelScope.launch {
            workoutRepo.getLatestSessionForTask(taskId)?.let { savedStateHandle[KEY_PENDING_SUMMARY] = it.id }
        }
    }

    fun cancelWorkoutCompletion() = _workoutCompletion.update { null }

    /**
     * Kullanıcı gerçekleşen setleri (akordeon ekranında) girip onayladığında: seanstan bir
     * WorkoutSession üretir ve kalıcılaştırır, programlı görevde döngüyü bir gün ilerletir ve
     * görevi tamamlanmış sayar. Yanlış tamamlamaya karşı Snackbar üzerinden geri alma sunulur.
     */
    fun submitWorkoutCompletion(actuals: List<ActualEntry>) {
        val request = _workoutCompletion.value ?: return
        viewModelScope.launch {
            val byId = actuals.associateBy { it.exerciseId }
            val groups = workoutRepo.getGroups()
            val resolved = resolveWorkout(request.task, groups)
            val workout = resolved?.second

            val loggedExercises = request.items.map { item ->
                // Akordeon ekranı her hareketin setlerini doğrudan LoggedSet olarak üretir;
                // anlamlı veri taşımayan boş setler ayıklanır.
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

            // Programlı görevde döngüyü bir gün ilerlet (rotasyon). Geri al için orijinal grubu sakla.
            var originalGroup: WorkoutGroup? = null
            if (request.task.linkedProgramId != null && resolved != null && workout != null) {
                val group = resolved.first
                if (group.workouts.size > 1) {
                    originalGroup = group
                    val nextIndex = (group.currentIndex + 1) % group.workouts.size
                    workoutRepo.upsertGroup(group.copy(currentIndex = nextIndex))
                }
            }

            // Görevi tamamla; geri alma anlık görüntüsünü seans/grup bilgisiyle birlikte burada kur.
            applyToggle(request.task, registerUndo = false)
            _undo.value = UndoableCompletion(
                token = undoTokenSeq.incrementAndGet(),
                messageRes = R.string.workout_completed,
                task = request.task,
                sessionId = session.id,
                programGroup = originalGroup,
            )
            // Onaylandı: taslağı temizle ve tamamlanan antrenmanın özet/sonuç sayfasını aç.
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
            // Görev silinince boşa çıkan geçici kategorileri otomatik temizle
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
