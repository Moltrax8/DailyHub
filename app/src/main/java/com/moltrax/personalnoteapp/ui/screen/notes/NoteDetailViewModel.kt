package com.moltrax.personalnoteapp.ui.screen.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Note
import com.moltrax.personalnoteapp.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteDetailState(val note: Note = Note(), val loaded: Boolean = false, val saving: Boolean = false, val missing: Boolean = false)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val repository: NoteRepository,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NoteDetailState())
    val state = mutableState.asStateFlow()

    fun load(id: String) {
        if (mutableState.value.loaded) return
        viewModelScope.launch {
            val original = if (id == "new") Note() else repository.getById(id)
            if (original == null) {
                mutableState.value = NoteDetailState(missing = true)
                return@launch
            }
            mutableState.value = NoteDetailState(original.copy(
                id = saved["draft_id"] ?: original.id,
                title = saved["draft_title"] ?: original.title,
                body = saved["draft_body"] ?: original.body,
                isPinned = saved["draft_pin"] ?: original.isPinned,
            ), loaded = true)
        }
    }

    fun edit(title: String = state.value.note.title, body: String = state.value.note.body,
             pinned: Boolean = state.value.note.isPinned) {
        val note = state.value.note.copy(title = title, body = body, isPinned = pinned)
        mutableState.value = state.value.copy(note = note)
        saved["draft_id"] = note.id
        saved["draft_title"] = title
        saved["draft_body"] = body
        saved["draft_pin"] = pinned
    }

    fun save(onDone: () -> Unit) {
        if (!state.value.loaded || state.value.saving) return
        viewModelScope.launch {
            mutableState.value = state.value.copy(saving = true)
            repository.upsert(state.value.note.copy(updatedAt = System.currentTimeMillis()))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(state.value.note.id)
            onDone()
        }
    }
}
