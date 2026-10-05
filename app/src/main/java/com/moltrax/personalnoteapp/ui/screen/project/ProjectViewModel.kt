package com.moltrax.personalnoteapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Project
import com.moltrax.personalnoteapp.domain.model.ProjectComment
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.ProjectStatus
import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubPublicRepo
import com.moltrax.personalnoteapp.domain.model.GithubRepo
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceType
import com.moltrax.personalnoteapp.domain.repository.GitHubRepository
import com.moltrax.personalnoteapp.domain.repository.ProjectRepository
import com.moltrax.personalnoteapp.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectDetailUiState(
    val project: Project? = null,
    val items: List<ProjectItem> = emptyList(),
    val comments: List<ProjectComment> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Project Manager UI state (Phase 6). Board = lightweight columns
 * (Idea → Planned → Developing → Finished), moves via arrows (no Jira drag).
 */
@HiltViewModel
class ProjectViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val spaces: SpaceRepository,
    private val github: GitHubRepository,
) : ViewModel() {

    val myProjects: StateFlow<List<Space>> = spaces.observeSpaces()
        .map { list -> list.filter { it.type == SpaceType.PROJECT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _detail = MutableStateFlow(ProjectDetailUiState())
    val detail: StateFlow<ProjectDetailUiState> = _detail.asStateFlow()

    fun openProject(spaceId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
        viewModelScope.launch {
            projects.observeItems(spaceId).collect { items ->
                _detail.update { it.copy(items = items, busy = false) }
            }
        }
    }

    fun observeProject(spaceId: String) = projects.observeProject(spaceId)

    fun observeComments(spaceId: String, refId: String) = projects.observeComments(spaceId, refId)

    fun createProject(name: String, description: String?, onCreated: (String) -> Unit) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { projects.createProject(clean, description?.takeIf { it.isNotBlank() }) }
                .onSuccess { _detail.update { it.copy(busy = false) }; onCreated(it.id) }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun addItem(spaceId: String, title: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { projects.addItem(spaceId, title) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun moveItem(spaceId: String, item: ProjectItem, status: ProjectStatus) {
        if (item.status == status) return
        viewModelScope.launch {
            runCatching { projects.moveItem(item, status); projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun editItem(spaceId: String, itemId: String, title: String, body: String?, url: String?) {
        viewModelScope.launch {
            runCatching { projects.editItem(itemId, title, body, url); projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteItem(spaceId: String, itemId: String) {
        viewModelScope.launch {
            runCatching { projects.deleteItem(itemId); projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun addComment(spaceId: String, refId: String, body: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { projects.addComment(spaceId, "project_item", refId, body); projects.pullProject(spaceId) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteComment(spaceId: String, commentId: String) {
        viewModelScope.launch {
            runCatching { projects.deleteComment(commentId); projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun deleteProject(spaceId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { projects.deleteProject(spaceId) }
                .onSuccess { onDone() }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    // -- GitHub tab (Phase 7) -------------------------------------------------

    data class GitHubUiState(
        val connected: GithubConnection? = null,
        val repos: List<GithubRepo> = emptyList(),
        val activity: List<GithubActivity> = emptyList(),
        val prefs: Map<String, Boolean> = emptyMap(),
        val busy: Boolean = false,
        val error: String? = null,
        val pushDone: Boolean = false,
    )

    private val _github = MutableStateFlow(GitHubUiState())
    val githubState: StateFlow<GitHubUiState> = _github.asStateFlow()

    fun loadGitHub(spaceId: String) {
        viewModelScope.launch {
            _github.update { it.copy(busy = true, error = null) }
            runCatching {
                val connection = github.myConnection()
                val repos = github.spaceRepos(spaceId)
                val prefs = github.notifPrefs(spaceId)
                Triple(connection, repos, prefs)
            }.onSuccess { (connection, repos, prefs) ->
                _github.update { it.copy(connected = connection, repos = repos, prefs = prefs, busy = false) }
                refreshGitHubActivity(spaceId)
            }.onFailure { e ->
                _github.update { it.copy(busy = false, error = e.message) }
            }
        }
        viewModelScope.launch {
            github.observeActivity(spaceId).collect { feed ->
                _github.update { it.copy(activity = feed) }
            }
        }
    }

    fun refreshGitHubActivity(spaceId: String) {
        viewModelScope.launch {
            runCatching { github.refreshActivity(spaceId) }
                .onFailure { e -> _github.update { it.copy(error = e.message) } }
        }
    }

    fun linkRepo(spaceId: String, repoId: Long, fullName: String, private: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { github.linkRepo(spaceId, repoId, fullName, private) }
                .onSuccess { loadGitHub(spaceId); onDone() }
                .onFailure { e -> _github.update { it.copy(error = e.message) } }
        }
    }

    fun unlinkRepo(spaceId: String, repoId: Long) {
        viewModelScope.launch {
            runCatching { github.unlinkRepo(repoId) }
                .onSuccess { loadGitHub(spaceId) }
                .onFailure { e -> _github.update { it.copy(error = e.message) } }
        }
    }

    fun togglePref(spaceId: String, kind: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { github.setNotifPref(spaceId, kind, enabled) }
                .onSuccess { loadGitHub(spaceId) }
                .onFailure { e -> _github.update { it.copy(error = e.message) } }
        }
    }

    fun enablePush() {
        _github.update { it.copy(busy = true, error = null, pushDone = false) }
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                viewModelScope.launch {
                    runCatching { github.registerFcmToken(token) }
                        .onSuccess { _github.update { it.copy(busy = false, pushDone = true) } }
                        .onFailure { e -> _github.update { it.copy(busy = false, error = e.message) } }
                }
            }
            .addOnFailureListener { e ->
                _github.update {
                    it.copy(
                        busy = false,
                        error = e.message ?: "Could not reach the push service — check network / Play services.",
                    )
                }
            }
            .addOnCanceledListener {
                _github.update { it.copy(busy = false, error = "Push setup was cancelled — try again.") }
            }
    }

    // -- Repo picker: track/untrack a GitHub user's public repos -----------------

    data class RepoBrowseUiState(
        val username: String = "",
        val repos: List<GithubPublicRepo> = emptyList(),
        /** Ids already linked in this space. */
        val trackedIds: Set<Long> = emptySet(),
        /** Checkbox state; Save links newly checked + unlinks newly unchecked. */
        val selectedIds: Set<Long> = emptySet(),
        /** Forks hidden by default — most users only track their own repos. */
        val hideForks: Boolean = true,
        val busy: Boolean = false,
        val error: String? = null,
    ) {
        val visibleRepos: List<GithubPublicRepo> =
            if (hideForks) repos.filter { !it.fork } else repos
    }

    private val _browse = MutableStateFlow(RepoBrowseUiState())
    val browseState: StateFlow<RepoBrowseUiState> = _browse.asStateFlow()

    fun fetchBrowseRepos(spaceId: String, username: String) {
        val clean = username.trim().trimStart('@')
        if (clean.isEmpty()) {
            _browse.update { it.copy(error = "Enter a GitHub username first.") }
            return
        }
        viewModelScope.launch {
            _browse.update { it.copy(busy = true, error = null, username = clean) }
            runCatching {
                val repos = github.publicRepos(clean)
                val tracked = github.spaceRepos(spaceId).map { it.id }.toSet()
                Triple(repos, tracked, tracked)
            }.onSuccess { (repos, tracked, selected) ->
                _browse.update {
                    it.copy(repos = repos, trackedIds = tracked, selectedIds = selected, busy = false)
                }
            }.onFailure { e ->
                _browse.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    fun toggleBrowseRepo(id: Long) {
        _browse.update {
            val sel = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = sel)
        }
    }

    fun toggleHideForks() {
        _browse.update { it.copy(hideForks = !it.hideForks) }
    }

    fun applyBrowseRepos(spaceId: String, onDone: () -> Unit = {}) {
        val s = _browse.value
        if (s.busy) return
        viewModelScope.launch {
            _browse.update { it.copy(busy = true, error = null) }
            val toLink = s.repos.filter { it.id in s.selectedIds && it.id !in s.trackedIds }
            val toUnlink = s.trackedIds.filter { it !in s.selectedIds }
            val failure = runCatching {
                toLink.forEach { github.linkRepo(spaceId, it.id, it.fullName, it.private) }
                toUnlink.forEach { github.unlinkRepo(it) }
            }.exceptionOrNull()
            if (failure != null) {
                _browse.update { it.copy(busy = false, error = failure.message) }
            } else {
                _browse.update { it.copy(busy = false) }
                loadGitHub(spaceId)
                onDone()
            }
        }
    }
}
