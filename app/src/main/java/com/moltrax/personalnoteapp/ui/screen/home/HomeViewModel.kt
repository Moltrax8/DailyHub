package com.moltrax.personalnoteapp.ui.screen.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moltrax.personalnoteapp.domain.model.Category
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.restoringCompletionFrom
import com.moltrax.personalnoteapp.domain.model.toggledCompletion
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.domain.util.matchesTags
import com.moltrax.personalnoteapp.domain.util.orderInCategory
import com.moltrax.personalnoteapp.widget.TaskWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TaskStatus { ACTIVE, ALL, DONE }

data class HomeUiState(
    val tasks: List<Task> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategories: Set<String> = emptySet(),
    val query: String = "",
    val status: TaskStatus = TaskStatus.ACTIVE,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskRepo: TaskRepository,
    private val categoryRepo: CategoryRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    private val status = MutableStateFlow(TaskStatus.ACTIVE)

    val state: StateFlow<HomeUiState> = combine(
        taskRepo.observeAll(),
        categoryRepo.observeAll(),
        query,
        selectedCategories,
        status,
    ) { tasks, categories, q, selected, status ->
        val normalizedQuery = q.trim()
        val filtered = tasks.filter { task ->
            val visible = when (status) {
                TaskStatus.ACTIVE -> !task.isDone
                TaskStatus.DONE -> task.isDone
                TaskStatus.ALL -> true
            }
            val matchesQuery = normalizedQuery.isEmpty() ||
                task.title.contains(normalizedQuery, ignoreCase = true) ||
                task.notes?.contains(normalizedQuery, ignoreCase = true) == true ||
                task.categoryNames.any { it.contains(normalizedQuery, ignoreCase = true) }
            val matchesCategory = selected.isEmpty() ||
                task.matchesTags(selected, matchAll = false)
            visible && matchesQuery && matchesCategory
        }
        HomeUiState(
            tasks = if (selected.size == 1) filtered.sortedBy { task ->
                task.orderInCategory(selected.single()) ?: Long.MAX_VALUE
            } else filtered,
            categories = categories,
            selectedCategories = selected,
            query = q,
            status = status,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun setQuery(value: String) { query.value = value }

    fun toggleCategory(name: String) {
        selectedCategories.value = selectedCategories.value.toMutableSet().apply {
            if (!add(name)) remove(name)
        }
    }

    fun clearCategories() { selectedCategories.value = emptySet() }

    fun setStatus(value: TaskStatus) { status.value = value }

    fun toggleDone(task: Task, onDone: (Task) -> Unit) {
        viewModelScope.launch {
            val current = taskRepo.getById(task.id) ?: return@launch
            taskRepo.upsert(current.toggledCompletion(System.currentTimeMillis()))
            TaskWidget.requestUpdate(context)
            onDone(current)
        }
    }

    fun undoCompletion(before: Task) {
        viewModelScope.launch {
            val current = taskRepo.getById(before.id) ?: return@launch
            taskRepo.upsert(current.restoringCompletionFrom(before, System.currentTimeMillis()))
        }
    }

    fun reorder(ids: List<String>) {
        viewModelScope.launch {
            val selected = selectedCategories.value
            if (selected.size == 1) taskRepo.reorderInCategory(selected.single(), ids)
            else taskRepo.reorderGlobal(ids)
        }
    }

    fun createCategory(name: String) { viewModelScope.launch { categoryRepo.ensureExists(name, true) } }
    fun renameCategory(old: String, new: String) {
        viewModelScope.launch {
            categoryRepo.rename(old, new)
            if (old in selectedCategories.value) selectedCategories.value = selectedCategories.value - old + new
        }
    }
    fun deleteCategory(name: String) {
        viewModelScope.launch {
            categoryRepo.delete(name)
            selectedCategories.value -= name
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            taskRepo.delete(task.id)
            categoryRepo.cleanupTemporary()
            TaskWidget.requestUpdate(context)
        }
    }
}
