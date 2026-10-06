package com.moltrax.personalnoteapp.ui.screen.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.data.remote.supabase.SupabaseAuthService
import com.moltrax.personalnoteapp.domain.model.FriendRequest
import com.moltrax.personalnoteapp.domain.model.FriendRequestStatus
import com.moltrax.personalnoteapp.domain.model.SupabaseProfile
import com.moltrax.personalnoteapp.domain.model.isValidUsername
import com.moltrax.personalnoteapp.domain.repository.ProfileRepository
import com.moltrax.personalnoteapp.domain.repository.SocialRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SocialUiState(
    val myId: String? = null,
    val myProfile: SupabaseProfile? = null,
    val query: String = "",
    val results: List<SupabaseProfile> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val friends: List<SupabaseProfile> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Friends graph UI state (Phase 4). Refresh on open; incoming list also ticks
 * via the repository poll while these screens live (true push in Phase 7).
 */
@HiltViewModel
class SocialViewModel @Inject constructor(
    private val auth: SupabaseAuthService,
    private val profiles: ProfileRepository,
    private val social: SocialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SocialUiState())
    val state: StateFlow<SocialUiState> = _state.asStateFlow()

    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            val uid = auth.currentUserId() ?: return@launch
            _state.update { it.copy(myId = uid) }
            refresh()
            observeJob = viewModelScope.launch {
                social.observeIncoming().collect { list ->
                    _state.update { it.copy(incoming = list) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val uid = _state.value.myId ?: auth.currentUserId() ?: return@launch
            _state.update { it.copy(myId = uid, busy = true, error = null) }
            runCatching {
                val profile = profiles.getMyProfile(uid)
                val incoming = social.incoming()
                val outgoing = social.outgoing()
                val friends = social.friends()
                _state.update {
                    it.copy(
                        myProfile = profile, incoming = incoming, outgoing = outgoing,
                        friends = friends, busy = false,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, error = e.message ?: "Load failed.") }
            }
        }
    }

    fun search(q: String) {
        _state.update { it.copy(query = q) }
        if (q.trim().length < 2) {
            _state.update { it.copy(results = emptyList()) }
            return
        }
        viewModelScope.launch {
            runCatching { social.searchByUsername(q.trim()) }
                .onSuccess { list -> _state.update { it.copy(results = list) } }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun send(toUserId: String) = mutate { social.sendRequest(toUserId); refresh() }

    fun cancel(requestId: String) = mutate { social.cancelRequest(requestId); refresh() }

    fun accept(requestId: String) = mutate { social.acceptRequest(requestId); refresh() }

    fun reject(requestId: String) = mutate { social.rejectRequest(requestId); refresh() }

    fun removeFriend(friendUserId: String) = mutate { social.removeFriend(friendUserId); refresh() }

    /** Renames the own profile (username unique, server-enforced). */
    fun renameMe(name: String, onDone: () -> Unit = {}) {
        val clean = name.trim()
        if (!isValidUsername(clean)) {
            _state.update { it.copy(error = "Username: 3–20 chars, letters/digits/_/., starts with letter/digit.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val uid = auth.currentUserId() ?: throw IllegalStateException("Not signed in.")
                val current = profiles.getMyProfile(uid)
                    ?: SupabaseProfile(id = uid, username = clean)
                profiles.upsertMyProfile(current.copy(username = clean))
                refresh()
            }.onSuccess { onDone() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Syncs the public display name to the server profile (username stays untouched). */
    fun setMyDisplayName(name: String) {
        viewModelScope.launch {
            runCatching {
                val uid = auth.currentUserId() ?: return@runCatching
                val current = profiles.getMyProfile(uid) ?: return@runCatching
                profiles.upsertMyProfile(current.copy(displayName = name.trim().ifBlank { null }))
            }.onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    /** Uploads a new avatar (Photo Picker bytes) and stores its URL on the profile. */
    fun uploadMyAvatar(bytes: ByteArray, extension: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching {
                val uid = auth.currentUserId() ?: throw IllegalStateException("Not signed in.")
                val url = profiles.uploadAvatar(uid, bytes, extension)
                val current = profiles.getMyProfile(uid)
                    ?: SupabaseProfile(id = uid, username = "me")
                profiles.upsertMyProfile(current.copy(avatarUrl = url))
                refresh()
            }.onSuccess { _state.update { it.copy(busy = false) }; onDone() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Best-effort display name for a user id (known profiles, else shortened id). */
    fun displayNameOf(userId: String): String {
        val s = _state.value
        (s.results + s.friends + listOfNotNull(s.myProfile))
            .firstOrNull { it.id == userId }
            ?.let { return it.displayName?.takeIf { n -> n.isNotBlank() } ?: it.username }
        return userId.take(8)
    }

    fun statusOf(userId: String): FriendRequestStatus? {        val s = _state.value
        if (s.friends.any { it.id == userId }) return FriendRequestStatus.ACCEPTED
        (s.incoming + s.outgoing).firstOrNull {
            (it.fromId == userId || it.toId == userId)
        }?.let { return it.status }
        return null
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { block() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}
