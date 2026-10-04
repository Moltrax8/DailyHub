package com.moltrax.personalnoteapp.ui.screen.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
import com.moltrax.personalnoteapp.domain.model.SpaceType
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpaceDetailUiState(
    val space: Space? = null,
    val members: List<SpaceMember> = emptyList(),
    val notes: List<SharedNote> = emptyList(),
    val tasks: List<SharedTask> = emptyList(),
    val links: List<SpaceLink> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Shared spaces UI state (Phase 5). Pulls on open + manual refresh; live
 * Realtime updates arrive in Phase 9.
 */
@HiltViewModel
class SpaceViewModel @Inject constructor(
    private val spaces: SpaceRepository,
) : ViewModel() {

    val mySpaces: StateFlow<List<Space>> = spaces.observeSpaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow(SpaceDetailUiState())
    val detail: StateFlow<SpaceDetailUiState> = _detail.asStateFlow()

    fun openSpace(spaceId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { spaces.pullSpace(spaceId) }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
        viewModelScope.launch {
            spaces.observeMembers(spaceId).collect { members ->
                _detail.update { it.copy(members = members) }
            }
        }
        viewModelScope.launch {
            spaces.observeNotes(spaceId).collect { notes ->
                _detail.update { it.copy(notes = notes, busy = false) }
            }
        }
        viewModelScope.launch {
            spaces.observeTasks(spaceId).collect { tasks ->
                _detail.update { it.copy(tasks = tasks) }
            }
        }
        viewModelScope.launch {
            spaces.observeLinks(spaceId).collect { links ->
                _detail.update { it.copy(links = links) }
            }
        }
    }

    fun createDuo(friendUserId: String, name: String?, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { spaces.createDuo(friendUserId, name) }
                .onSuccess { _detail.update { it.copy(busy = false) }; onCreated(it.id) }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Opens the existing Duo hub with a friend, creating it when missing. */
    fun openOrCreateDuo(friendUserId: String, name: String?, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching {
                val mine = mySpaces.first()
                for (s in mine.filter { it.type == SpaceType.DUO }) {
                    if (spaces.getMembers(s.id).any { it.userId == friendUserId }) {
                        _detail.update { it.copy(busy = false) }
                        onOpen(s.id)
                        return@launch
                    }
                }
                val created = spaces.createDuo(friendUserId, name)
                _detail.update { it.copy(busy = false) }
                onOpen(created.id)
            }.onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun refresh(spaceId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { spaces.pullSpace(spaceId) }
                .onSuccess { _detail.update { it.copy(busy = false) } }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun addNote(spaceId: String, title: String?, body: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { spaces.addNote(spaceId, title, body) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteNote(spaceId: String, noteId: String) {
        viewModelScope.launch {
            runCatching { spaces.deleteNote(noteId); spaces.pullSpace(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun addSharedTask(spaceId: String, title: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { spaces.addSharedTask(spaceId, title) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun toggleSharedTask(spaceId: String, task: SharedTask) {
        viewModelScope.launch {
            runCatching { spaces.toggleSharedTask(task); spaces.pullSpace(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteSharedTask(spaceId: String, taskId: String) {
        viewModelScope.launch {
            runCatching { spaces.deleteSharedTask(taskId); spaces.pullSpace(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun addLink(spaceId: String, url: String, title: String?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { spaces.addLink(spaceId, url, title) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteLink(spaceId: String, linkId: String) {
        viewModelScope.launch {
            runCatching { spaces.deleteLink(linkId); spaces.pullSpace(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun inviteMember(spaceId: String, userId: String) {
        viewModelScope.launch {
            runCatching { spaces.inviteMember(spaceId, userId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }
}
