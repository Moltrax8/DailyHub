package com.moltrax.personalnoteapp.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.repository.GitHubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GithubSettingsUiState(
    val login: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * GitHub link status for the Settings row. Sign-in stays Google-only; this
 * only links a GitHub account for repo picking (token stays server-side).
 */
@HiltViewModel
class GithubSettingsViewModel @Inject constructor(
    private val github: GitHubRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GithubSettingsUiState(busy = true))
    val state: StateFlow<GithubSettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { github.myConnection() }
                .onSuccess { c -> _state.update { it.copy(login = c?.githubLogin, busy = false) } }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Starts the link flow; on success [onUrl] receives the authorize URL to open. */
    fun startConnect(onUrl: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { github.connectStart() }
                .onSuccess { url -> _state.update { it.copy(busy = false) }; onUrl(url) }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    /** Completes the link after the `github-callback` deep link. */
    fun finishLink(code: String, state: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { github.connectFinish(code, state) }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            runCatching { github.disconnectGitHub() }
                .onSuccess { refresh() }
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
