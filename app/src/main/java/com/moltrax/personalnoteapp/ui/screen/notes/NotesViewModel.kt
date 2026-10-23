package com.moltrax.personalnoteapp.ui.screen.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Note
import com.moltrax.personalnoteapp.domain.repository.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class NotesUiState(
    val notes: List<Note> = emptyList(),
    val query: String = "",
    val loaded: Boolean = false,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    noteRepo: NoteRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<NotesUiState> = combine(noteRepo.observeAll(), query) { notes, q ->
        val needle = q.trim()
        NotesUiState(
            notes = if (needle.isEmpty()) notes else notes.filter {
                it.title.contains(needle, ignoreCase = true) || it.body.contains(needle, ignoreCase = true)
            },
            query = q,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setQuery(value: String) { query.value = value }
}
