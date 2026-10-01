package com.moltrax.personalnoteapp.ui.screen.focus

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.withCompletion
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.widget.TaskWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FocusState(
    val task: Task? = null,
    val totalSeconds: Int = 1500,
    val remainingSeconds: Int = 1500,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
) {
    val progress: Float get() = if (totalSeconds == 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds
    val minutesLeft: Int get() = remainingSeconds / 60
    val secondsLeft: Int get() = remainingSeconds % 60
}

@HiltViewModel
class FocusTimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepo: TaskRepository,
    private val notifService: NotificationService,
    private val syncRepo: SyncRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()

    // Spora linkli görevde sayaç bitince tamamlamak yerine Home'daki tamamlama ekranına
    // yönlendiren Snackbar gösterilir (isCompleted kurulmaz, ekrandan çıkılmaz).
    private val _linkedTaskNotice = MutableStateFlow(false)
    val linkedTaskNotice: StateFlow<Boolean> = _linkedTaskNotice.asStateFlow()

    fun consumeLinkedNotice() { _linkedTaskNotice.value = false }

    private var timerJob: Job? = null

    fun load(taskId: String) {
        viewModelScope.launch {
            val task = taskRepo.getById(taskId) ?: return@launch
            _state.update { it.copy(task = task, totalSeconds = task.focusDurationSeconds, remainingSeconds = task.focusDurationSeconds) }
        }
    }

    fun start() {
        if (_state.value.isRunning) return
        _state.update { it.copy(isRunning = true) }
        timerJob = viewModelScope.launch {
            while (_state.value.remainingSeconds > 0 && _state.value.isRunning) {
                delay(1_000)
                _state.update { it.copy(remainingSeconds = it.remainingSeconds - 1) }
            }
            if (_state.value.remainingSeconds == 0) {
                val task = _state.value.task
                // Linkli görevde tamamlanmış gösterme; Home tamamlama ekranına yönlendir.
                if (task != null && (task.linkedWorkoutId != null || task.linkedProgramId != null)) {
                    _state.update { it.copy(isRunning = false) }
                    _linkedTaskNotice.value = true
                } else {
                    _state.update { it.copy(isRunning = false, isCompleted = true) }
                    markDone()
                }
            }
        }
    }

    fun pause() {
        timerJob?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun reset() {
        timerJob?.cancel()
        _linkedTaskNotice.value = false
        _state.update { it.copy(isRunning = false, isCompleted = false, remainingSeconds = it.totalSeconds) }
    }

    private suspend fun markDone() {
        val task = _state.value.task ?: return
        // Spora/programa linkli görevler odak zamanlayıcısıyla KAPATILMAZ. Doğru tamamlama akışı
        // (set/tekrar/ağırlık girişi → WorkoutSession kaydı → programlı görevde gün rotasyonu) yalnızca
        // ana ekrandaki tik ile çalışır. Odak sayacından kapatmak seansı kaydetmeden görevi bitirir ve
        // program gününü ilerletmezdi (iki tamamlama yolu arasında tutarsızlık). Görev açık bırakılır;
        // kullanıcı ana ekrandan tikleyip antrenmanı usulünce tamamlar.
        if (task.linkedWorkoutId != null || task.linkedProgramId != null) return
        // Tekrarlayan görevi ileri sar, normal görevi kapat (ana listeyle tutarlı)
        val result = task.withCompletion()
        taskRepo.upsert(result)
        notifService.cancelReminder(task.id)
        if (!result.isDone && prefs.systemAlertsEnabled.first()) {
            notifService.scheduleReminder(result, prefs.reminderMinutes.first())
        }
        syncRepo.pushToDrive()
        TaskWidget.requestUpdate(context)
    }

    override fun onCleared() { timerJob?.cancel(); super.onCleared() }
}
