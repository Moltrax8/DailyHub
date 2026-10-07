package com.moltrax.personalnoteapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Project
import com.moltrax.personalnoteapp.domain.model.ProjectComment
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.ProjectStatus
import com.moltrax.personalnoteapp.domain.model.GithubActivity
import com.moltrax.personalnoteapp.domain.model.GithubAppRepo
import com.moltrax.personalnoteapp.domain.model.GithubConnection
import com.moltrax.personalnoteapp.domain.model.GithubImportTarget
import com.moltrax.personalnoteapp.domain.model.GithubPublicRepo
import com.moltrax.personalnoteapp.domain.model.GithubRepo
import com.moltrax.personalnoteapp.domain.model.dedupeImportProjectName
import com.moltrax.personalnoteapp.domain.model.filterGithubAppRepos
import com.moltrax.personalnoteapp.domain.model.importAlreadyAddedIds
import com.moltrax.personalnoteapp.domain.model.importProjectName
import com.moltrax.personalnoteapp.domain.model.importRepoOwner
import com.moltrax.personalnoteapp.domain.model.toggleImportSelection
import com.moltrax.personalnoteapp.domain.repository.GithubNotConnectedException
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
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

    private var openJob: Job? = null
    private var githubJob: Job? = null

    fun openProject(spaceId: String) {
        viewModelScope.launch {
            _detail.update { it.copy(busy = true, error = null) }
            runCatching { projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(busy = false, error = e.message) } }
        }
        openJob?.cancel()
        openJob = viewModelScope.launch {
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
            val mutationError = runCatching { projects.moveItem(item, status) }.exceptionOrNull()
            if (mutationError != null) {
                _detail.update { it.copy(error = mutationError.message) }
                return@launch
            }
            // Mutation succeeded: a refresh failure is a sync problem, not a move failure.
            runCatching { projects.pullProject(spaceId) }
                .onFailure { e -> _detail.update { it.copy(error = e.message) } }
        }
    }

    fun editItem(spaceId: String, itemId: String, title: String, body: String?, url: String?) {
        viewModelScope.launch {
            val mutationError = runCatching { projects.editItem(itemId, title, body, url) }.exceptionOrNull()
            if (mutationError != null) {
                _detail.update { it.copy(error = mutationError.message) }
                return@launch
            }
            runCatching { projects.pullProject(spaceId) }
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
        val connectBusy: Boolean = false,
        /** Set when `start` returns an authorize URL — UI opens it, then consumes. */
        val authUrl: String? = null,
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
        githubJob?.cancel()
        githubJob = viewModelScope.launch {
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
                .onSuccess { loadGitHub(spaceId); loadAppRepos(spaceId) }
                .onFailure { e -> _github.update { it.copy(error = e.message) } }
        }
    }

    // -- GitHub App linking (connect / deep-link finish / disconnect) ----------

    /** Starts the link flow; on success [onUrl] receives the authorize URL to open. */
    fun startGithubConnect(onUrl: (String) -> Unit) {
        viewModelScope.launch {
            _github.update { it.copy(connectBusy = true, error = null, authUrl = null) }
            runCatching { github.connectStart() }
                .onSuccess { url ->
                    _github.update { it.copy(connectBusy = false, authUrl = url) }
                    onUrl(url)
                }
                .onFailure { e ->
                    _github.update { it.copy(connectBusy = false, error = e.message) }
                }
        }
    }

    fun consumeAuthUrl() {
        _github.update { it.copy(authUrl = null) }
    }

    fun reportGithubError(message: String) {
        _github.update { it.copy(error = message) }
    }

    /** Completes the link after the `github-callback` deep link, then refreshes. */
    fun finishGithubLink(spaceId: String, code: String, state: String) {
        viewModelScope.launch {
            _github.update { it.copy(connectBusy = true, error = null) }
            runCatching { github.connectFinish(code, state) }
                .onSuccess {
                    _github.update { it.copy(connectBusy = false) }
                    loadGitHub(spaceId)
                    loadAppRepos(spaceId)
                }
                .onFailure { e ->
                    _github.update { it.copy(connectBusy = false, error = e.message) }
                }
        }
    }

    fun disconnectGithub(spaceId: String? = null) {
        viewModelScope.launch {
            _github.update { it.copy(connectBusy = true, error = null) }
            runCatching { github.disconnectGitHub() }
                .onSuccess {
                    _appRepos.update { AppReposUiState() }
                    _github.update { it.copy(connectBusy = false) }
                    if (spaceId != null) loadGitHub(spaceId)
                    else refreshConnection()
                }
                .onFailure { e ->
                    _github.update { it.copy(connectBusy = false, error = e.message) }
                }
        }
    }

    /** Refreshes just the connection badge (e.g. Settings row after a link). */
    fun refreshConnection() {
        viewModelScope.launch {
            runCatching { github.myConnection() }
                .onSuccess { c -> _github.update { it.copy(connected = c) } }
                .onFailure { e -> _github.update { it.copy(error = e.message) } }
        }
    }

    // -- My repos picker: the linked account's repos BY NAME -------------------

    data class AppReposUiState(
        val login: String = "",
        val repos: List<GithubAppRepo> = emptyList(),
        val installUrl: String? = null,
        val query: String = "",
        val hideForks: Boolean = false,
        /** Ids already linked in this space. */
        val trackedIds: Set<Long> = emptySet(),
        /** Checkbox state; Save links newly checked + unlinks newly unchecked. */
        val selectedIds: Set<Long> = emptySet(),
        val busy: Boolean = false,
        val notConnected: Boolean = false,
        val error: String? = null,
    ) {
        val visibleRepos: List<GithubAppRepo> =
            filterGithubAppRepos(repos, query, hideForks)
    }

    private val _appRepos = MutableStateFlow(AppReposUiState())
    val appReposState: StateFlow<AppReposUiState> = _appRepos.asStateFlow()

    fun loadAppRepos(spaceId: String) {
        viewModelScope.launch {
            _appRepos.update { it.copy(busy = true, error = null, notConnected = false) }
            runCatching {
                val result = github.appRepos()
                val tracked = github.spaceRepos(spaceId).map { it.id }.toSet()
                Triple(result, tracked, tracked)
            }.onSuccess { (result, tracked, selected) ->
                _appRepos.update {
                    it.copy(
                        login = result.login,
                        repos = result.repos,
                        installUrl = result.installUrl,
                        trackedIds = tracked,
                        selectedIds = selected,
                        busy = false,
                    )
                }
            }.onFailure { e ->
                if (e is GithubNotConnectedException) {
                    _appRepos.update { it.copy(busy = false, notConnected = true) }
                } else {
                    _appRepos.update { it.copy(busy = false, error = e.message) }
                }
            }
        }
    }

    fun setAppRepoQuery(query: String) {
        _appRepos.update { it.copy(query = query) }
    }

    fun toggleAppRepo(id: Long) {
        _appRepos.update {
            val sel = if (id in it.selectedIds) it.selectedIds - id else it.selectedIds + id
            it.copy(selectedIds = sel)
        }
    }

    fun toggleHideAppForks() {
        _appRepos.update { it.copy(hideForks = !it.hideForks) }
    }

    fun applyAppRepos(spaceId: String, onDone: () -> Unit = {}) {
        val s = _appRepos.value
        if (s.busy) return
        viewModelScope.launch {
            _appRepos.update { it.copy(busy = true, error = null) }
            val byId = s.repos.associateBy { it.id }
            val toLink = s.repos.filter { it.id in s.selectedIds && it.id !in s.trackedIds }
            val toUnlink = s.trackedIds.filter { it !in s.selectedIds }
            val failure = runCatching {
                toLink.forEach {
                    val known = byId[it.id]
                    github.linkRepo(spaceId, it.id, it.fullName, known?.private ?: it.private)
                }
                toUnlink.forEach { github.unlinkRepo(it) }
            }.exceptionOrNull()
            if (failure != null) {
                _appRepos.update { it.copy(busy = false, error = failure.message) }
            } else {
                _appRepos.update { it.copy(busy = false) }
                loadGitHub(spaceId)
                loadAppRepos(spaceId)
                onDone()
            }
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
                loadAppRepos(spaceId)
                onDone()
            }
        }
    }

    // -- Projects-list GitHub import (entry point on the list screen) --------

    data class GithubImportUiState(
        val repos: List<GithubAppRepo> = emptyList(),
        val installUrl: String? = null,
        val query: String = "",
        val hideForks: Boolean = false,
        /** Repo ids already linked to ANY project: shown, not selectable. */
        val alreadyAddedIds: Set<Long> = emptySet(),
        val selectedIds: Set<Long> = emptySet(),
        val target: GithubImportTarget = GithubImportTarget.NEW_PROJECT_EACH,
        val targetProjectId: String? = null,
        val loading: Boolean = false,
        val busy: Boolean = false,
        /** Signed out of the DailyHub account (server reports "Not signed in."). */
        val signedIn: Boolean = true,
        val notConnected: Boolean = false,
        val progressDone: Int = 0,
        val progressTotal: Int = 0,
        val error: String? = null,
        val failedNames: List<String> = emptyList(),
        val importedCount: Int = 0,
        /** Set when a run finishes; consumed by the dialog (see [consumeImportResult]). */
        val done: Boolean = false,
    ) {
        val visibleRepos: List<GithubAppRepo> =
            filterGithubAppRepos(repos, query, hideForks)
        val selectableCount: Int =
            visibleRepos.count { it.id in selectedIds && it.id !in alreadyAddedIds }
    }

    private val _import = MutableStateFlow(GithubImportUiState())
    val importState: StateFlow<GithubImportUiState> = _import.asStateFlow()

    /** Resets the result flags, then loads connection + repos + cross-project links. */
    fun prepareImport() {
        _import.update {
            it.copy(
                done = false,
                importedCount = 0,
                failedNames = emptyList(),
                error = null,
                progressDone = 0,
                progressTotal = 0,
            )
        }
        loadImport()
    }

    fun loadImport() {
        viewModelScope.launch {
            _import.update { it.copy(loading = true, error = null) }
            val connection = runCatching { github.myConnection() }.getOrElse { e ->
                if (e.message == "Not signed in.") {
                    _import.update { it.copy(loading = false, signedIn = false) }
                } else {
                    _import.update { it.copy(loading = false, error = e.message) }
                }
                return@launch
            }
            if (connection?.githubLogin == null) {
                _import.update { it.copy(loading = false, signedIn = true, notConnected = true) }
                return@launch
            }
            val result = runCatching { github.appRepos() }.getOrElse { e ->
                if (e is GithubNotConnectedException) {
                    _import.update { it.copy(loading = false, signedIn = true, notConnected = true) }
                } else {
                    _import.update { it.copy(loading = false, signedIn = true, error = e.message) }
                }
                return@launch
            }
            // Ids linked to ANY of my projects. Do not read myProjects.value here:
            // on a cold start it is still the empty initial value, so previously
            // linked repos would look selectable and fail with 409 on link.
            // Suspend-collect the first real emission from the spaces source.
            val mySpaces = spaces.observeSpaces().first().filter { it.type == SpaceType.PROJECT }
            val linked = runCatching {
                mySpaces.map { space ->
                    github.spaceRepos(space.id).map { it.id }
                }
            }.getOrElse { e ->
                _import.update { it.copy(loading = false, signedIn = true, error = e.message) }
                return@launch
            }
            val already = importAlreadyAddedIds(linked)
            _import.update {
                it.copy(
                    loading = false,
                    signedIn = true,
                    notConnected = false,
                    repos = result.repos,
                    installUrl = result.installUrl,
                    alreadyAddedIds = already,
                    // Drop selections that are stale or already linked.
                    selectedIds = it.selectedIds
                        .filter { id -> result.repos.any { r -> r.id == id } }
                        .filter { id -> id !in already }
                        .toSet(),
                )
            }
        }
    }

    fun setImportQuery(query: String) {
        _import.update { it.copy(query = query) }
    }

    fun toggleImportHideForks() {
        _import.update { it.copy(hideForks = !it.hideForks) }
    }

    fun toggleImportRepo(id: Long) {
        _import.update {
            if (id in it.alreadyAddedIds) it
            else it.copy(selectedIds = toggleImportSelection(it.selectedIds, id))
        }
    }

    fun setImportTarget(target: GithubImportTarget) {
        _import.update { it.copy(target = target) }
    }

    fun setImportTargetProject(spaceId: String?) {
        _import.update { it.copy(targetProjectId = spaceId) }
    }

    /** Completes the connect flow started from the import dialog, then reloads. */
    fun finishImportLink(code: String, state: String) {
        viewModelScope.launch {
            _import.update { it.copy(loading = true, error = null) }
            runCatching { github.connectFinish(code, state) }
                .onSuccess { loadImport() }
                .onFailure { e ->
                    _import.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun consumeImportResult() {
        _import.update { it.copy(done = false) }
    }

    /**
     * Runs the import: creates projects and/or links repos with the EXISTING
     * repository functions. Per-repo failures are collected without losing
     * the successful ones; [onResult] reports (imported, failedNames).
     */
    fun runImport(existingNames: List<String>, onResult: (Int, List<String>) -> Unit = { _, _ -> }) {
        val s = _import.value
        if (s.busy || s.loading) return
        val picked = s.repos.filter { it.id in s.selectedIds && it.id !in s.alreadyAddedIds }
        if (picked.isEmpty()) return
        if (s.target == GithubImportTarget.EXISTING_PROJECT && s.targetProjectId == null) return
        viewModelScope.launch {
            _import.update {
                it.copy(
                    busy = true,
                    error = null,
                    failedNames = emptyList(),
                    importedCount = 0,
                    done = false,
                    progressDone = 0,
                    progressTotal = picked.size,
                )
            }
            val failures = mutableListOf<String>()
            val linkedOk = mutableListOf<Long>()
            var ok = 0
            if (s.target == GithubImportTarget.NEW_PROJECT_EACH) {
                val taken = existingNames.toMutableList()
                picked.forEach { repo ->
                    val name = dedupeImportProjectName(
                        importProjectName(repo.fullName),
                        importRepoOwner(repo.fullName),
                        taken,
                    )
                    val res = runCatching {
                        val space = projects.createProject(
                            name,
                            repo.description?.takeIf { it.isNotBlank() },
                        )
                        try {
                            github.linkRepo(space.id, repo.id, repo.fullName, repo.private)
                            taken += name
                        } catch (e: Exception) {
                            // Do not leave an empty orphan project behind (a retry would create a
                            // second one): remove it, best effort, then report this repo as failed.
                            runCatching { projects.deleteProject(space.id) }
                            throw e
                        }
                    }
                    if (res.isSuccess) {
                        ok++
                        linkedOk += repo.id
                    } else {
                        failures += repo.fullName
                    }
                    _import.update { it.copy(progressDone = ok + failures.size) }
                }
            } else {
                val targetId = s.targetProjectId ?: return@launch
                picked.forEach { repo ->
                    val res = runCatching {
                        github.linkRepo(targetId, repo.id, repo.fullName, repo.private)
                    }
                    if (res.isSuccess) {
                        ok++
                        linkedOk += repo.id
                    } else {
                        failures += repo.fullName
                    }
                    _import.update { it.copy(progressDone = ok + failures.size) }
                }
            }
            _import.update {
                it.copy(
                    busy = false,
                    importedCount = ok,
                    failedNames = failures,
                    done = true,
                    alreadyAddedIds = it.alreadyAddedIds + linkedOk,
                    selectedIds = it.selectedIds - linkedOk.toSet(),
                )
            }
            refreshRepoCounts()
            onResult(ok, failures)
        }
    }

    // -- Linked-repo counts for the project list cards (chip, read-only) ------

    private val _repoCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val repoCounts: StateFlow<Map<String, Int>> = _repoCounts.asStateFlow()

    /** Best-effort per-project repo counts; failures stay silent (chip hides). */
    fun refreshRepoCounts() {
        viewModelScope.launch {
            val ids = myProjects.value.map { it.id }
            if (ids.isEmpty()) {
                _repoCounts.value = emptyMap()
                return@launch
            }
            val counts = _repoCounts.value.toMutableMap()
            // Drop rows for projects that no longer exist.
            counts.keys.retainAll(ids.toSet())
            ids.forEach { id ->
                runCatching { github.spaceRepos(id).size }
                    .onSuccess { counts[id] = it }
                    // On failure keep the previous value for this id (no wipe).
            }
            _repoCounts.value = counts
        }
    }
}
