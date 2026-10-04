package com.moltrax.personalnoteapp.ui.screen.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Project
import com.moltrax.personalnoteapp.domain.model.ProjectComment
import com.moltrax.personalnoteapp.domain.model.ProjectItem
import com.moltrax.personalnoteapp.domain.model.ProjectStatus
import com.moltrax.personalnoteapp.domain.model.Space
import com.moltrax.personalnoteapp.domain.model.SpaceType
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
}
