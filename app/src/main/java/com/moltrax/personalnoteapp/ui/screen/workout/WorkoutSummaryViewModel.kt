package com.moltrax.personalnoteapp.ui.screen.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.moltrax.personalnoteapp.data.json.WorkoutJsonCodec
import com.moltrax.personalnoteapp.data.local.storage.WorkoutFileManager
import com.moltrax.personalnoteapp.domain.model.WorkoutSession
import com.moltrax.personalnoteapp.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Data layer of the summary/result page of a completed workout session. Loads a single
 * [WorkoutSession] (that day's exercises + entered sets/reps/weight) from the session id.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    private val workoutRepo: WorkoutRepository,
    private val fileManager: WorkoutFileManager,
) : ViewModel() {

    private val _session = MutableStateFlow<WorkoutSession?>(null)
    val session: StateFlow<WorkoutSession?> = _session.asStateFlow()

    // Whether the initial load finished (a "loading" indicator until then; may be "not found" after).
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun load(sessionId: String) {
        if (_session.value?.id == sessionId) return
        viewModelScope.launch {
            _session.value = workoutRepo.getSessionById(sessionId)
            _loaded.value = true
        }
    }

    /**
     * Writes an EXACT copy of the displayed session as JSON to the user-picked document.
     * Read-only: creates no new session, modifies no existing session. Reports the result via [onDone].
     */
    fun exportResultToUri(session: WorkoutSession, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = fileManager
                .writeText(uri, WorkoutJsonCodec.encodeResult(session))
                .isSuccess
            onDone(ok)
        }
    }
}
