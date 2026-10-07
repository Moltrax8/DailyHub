package com.moltrax.personalnoteapp.ui.screen.space

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.FeedEntry
import com.moltrax.personalnoteapp.domain.model.SharedNote
import com.moltrax.personalnoteapp.domain.model.SharedTask
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceEvent
import com.moltrax.personalnoteapp.domain.model.SpaceFile
import com.moltrax.personalnoteapp.domain.model.SpaceLink
import com.moltrax.personalnoteapp.domain.model.SpaceMember
import com.moltrax.personalnoteapp.domain.model.SpaceMessage
import com.moltrax.personalnoteapp.domain.model.SpaceType
import com.moltrax.personalnoteapp.domain.repository.SocialRepository
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    // Phase 8 (online-only mirrors).
    val messages: List<SpaceMessage> = emptyList(),
    val events: List<SpaceEvent> = emptyList(),
    val files: List<SpaceFile> = emptyList(),
    val feed: List<FeedEntry> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Shared spaces UI state (Phase 5). Pulls on open + manual refresh; live
 * Realtime updates arrive in Phase 9.
 */
@HiltViewModel
class SpaceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spaces: SpaceRepository,
    private val social: SocialRepository,
) : ViewModel() {

    val mySpaces: StateFlow<List<Space>> = spaces.observeSpaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow(SpaceDetailUiState())
    val detail: StateFlow<SpaceDetailUiState> = _detail.asStateFlow()

    private var chatPoll: Job? = null

    fun openSpace(spaceId: String) {
        chatPoll?.cancel()
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching {
                spaces.pullSpace(spaceId)
                spaces.pullExtra(spaceId)
            }.onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
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
        viewModelScope.launch {
            spaces.observeMessages(spaceId).collect { messages ->
                _detail.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            spaces.observeEvents(spaceId).collect { events ->
                _detail.update { it.copy(events = events) }
            }
        }
        viewModelScope.launch {
            spaces.observeFiles(spaceId).collect { files ->
                _detail.update { it.copy(files = files) }
            }
        }
        viewModelScope.launch {
            spaces.observeFeed(spaceId).collect { feed ->
                _detail.update { it.copy(feed = feed) }
            }
        }
        // Chat poll until Realtime (Phase 9): refresh extra content while open.
        chatPoll = viewModelScope.launch {
            while (true) {
                delay(CHAT_POLL_MS)
                runCatching { spaces.pullExtra(spaceId) }
            }
        }
    }

    override fun onCleared() {
        chatPoll?.cancel()
        super.onCleared()
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

    /** Invites a collaborator by their DailyHub username (owner adds friends). */
    fun inviteByUsername(spaceId: String, username: String, onDone: () -> Unit = {}) {
        val clean = username.trim().trimStart('@')
        if (clean.isEmpty()) {
            _detail.update { it.copy(error = context.getString(R.string.space_err_invite_empty)) }
            return
        }
        viewModelScope.launch {
            runCatching {
                val match = social.searchByUsername(clean)
                    .firstOrNull { it.username.equals(clean, ignoreCase = true) }
                    ?: throw IllegalStateException(context.getString(R.string.space_err_user_not_found, clean))
                spaces.inviteMember(spaceId, match.id)
            }.onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun removeMember(spaceId: String, userId: String) {
        viewModelScope.launch {
            runCatching { spaces.removeMember(spaceId, userId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun leaveSpace(spaceId: String, onGone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { spaces.leaveSpace(spaceId) }
                .onSuccess { onGone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    // -- Phase 8 actions -----------------------------------------------------

    fun refreshExtra(spaceId: String) {
        viewModelScope.launch {
            runCatching { spaces.pullExtra(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun sendMessage(spaceId: String, body: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { spaces.sendMessage(spaceId, body) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteMessage(spaceId: String, messageId: String) {
        viewModelScope.launch {
            runCatching { spaces.deleteMessage(messageId); spaces.pullExtra(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun addEvent(spaceId: String, title: String, startAt: Long, endAt: Long?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { spaces.addEvent(spaceId, title, startAt, endAt) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteEvent(spaceId: String, eventId: String) {
        viewModelScope.launch {
            runCatching { spaces.deleteEvent(eventId); spaces.pullExtra(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun uploadFile(spaceId: String, fileName: String, bytes: ByteArray, mime: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { spaces.uploadFile(spaceId, fileName, bytes, mime) }
                .onSuccess { _detail.update { it.copy(busy = false) }; onDone() }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun deleteFile(spaceId: String, file: SpaceFile) {
        viewModelScope.launch {
            runCatching { spaces.deleteFile(file); spaces.pullExtra(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    suspend fun downloadBytes(file: SpaceFile): ByteArray =
        spaces.downloadFile(file)

    private companion object {
        // Chat refresh cadence until Realtime push (Phase 9).
        const val CHAT_POLL_MS = 15_000L
    }
}
