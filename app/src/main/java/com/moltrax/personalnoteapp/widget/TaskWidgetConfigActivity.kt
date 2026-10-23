package com.moltrax.personalnoteapp.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.repository.CategoryRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.ui.i18n.localizedConfiguration
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskWidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var categories: CategoryRepository
    @Inject lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finishAndRemoveTask(); return }
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@TaskWidgetConfigActivity).getGlanceIdBy(widgetId)
            val initial = WidgetFilter.load(getAppWidgetState(this@TaskWidgetConfigActivity, PreferencesGlanceStateDefinition, glanceId))
            val allTasks = tasks.getAll()
            val allTags = (categories.getAll().map { it.name } + allTasks.flatMap { it.categoryNames }).distinct().sorted()
            val language = preferences.language.first()
            val local = localizedFor(language)
            setContent {
                CompositionLocalProvider(LocalContext provides local,
                    LocalConfiguration provides localizedConfiguration(resources.configuration, language)) {
                    AppTheme {
                        ConfigDialog(initial, allTasks, allTags, onDismiss = { finishAndRemoveTask() }) { filter, reset ->
                            lifecycleScope.launch {
                                updateAppWidgetState(this@TaskWidgetConfigActivity, glanceId) { prefs ->
                                    WidgetFilter.save(prefs, filter)
                                    // Keep older multi-selected widgets intact on appearance-only edits.
                                    if (reset || filter.taskId.isNotBlank() || filter.categoryNames != initial.categoryNames) {
                                        prefs.remove(TaskWidget.SELECTED_TASK_IDS)
                                    }
                                    prefs.remove(TaskWidget.EXPANDED_TASK_IDS)
                                    prefs.remove(TaskWidget.UNDO_TASK_JSON)
                                    prefs.remove(TaskWidget.UNDO_TASK_TITLE)
                                }
                                TaskWidget().update(this@TaskWidgetConfigActivity, glanceId)
                                finishAndRemoveTask()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigDialog(initial: WidgetFilter, tasks: List<Task>, allTags: List<String>,
                          onDismiss: () -> Unit, onSave: (WidgetFilter, Boolean) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var single by rememberSaveable { mutableStateOf(initial.taskId.isNotBlank()) }
    var taskId by rememberSaveable { mutableStateOf(initial.taskId) }
    var tags by remember { mutableStateOf(initial.categoryNames) }
    var showDone by rememberSaveable { mutableStateOf(initial.showDone) }
    var matchAll by rememberSaveable { mutableStateOf(initial.matchAll) }
    var limit by rememberSaveable { mutableIntStateOf(initial.limit) }
    var showNotes by rememberSaveable { mutableStateOf(initial.showNotes) }
    var expandSubtasks by rememberSaveable { mutableStateOf(initial.expandSubtasks) }
    var showDoneSubtasks by rememberSaveable { mutableStateOf(initial.showDoneSubtasks) }
    var compact by rememberSaveable { mutableStateOf(initial.compact) }
    var sort by rememberSaveable { mutableStateOf(initial.sort.name) }
    var theme by rememberSaveable { mutableStateOf(initial.theme.name) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.widget_config_title)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(80) }, label = { Text(stringResource(R.string.widget_filter_title)) }, singleLine = true)
                Option(stringResource(R.string.widget_mode_list), !single, radio = true) { single = false }
                Option(stringResource(R.string.widget_mode_task), single, radio = true) { single = true }
                if (single) {
                    if (tasks.isEmpty()) Text(stringResource(R.string.widget_no_pending))
                    tasks.forEach { task ->
                        Option(task.title, taskId == task.id, radio = true) { taskId = task.id }
                    }
                    if (taskId.isNotBlank() && tasks.none { it.id == taskId }) Text(stringResource(R.string.widget_task_missing))
                } else {
                    Text(stringResource(R.string.widget_filter_tags))
                    allTags.forEach { tag ->
                        Option(tag, tag in tags) { tags = if (tag in tags) tags - tag else tags + tag }
                    }
                    Option(stringResource(R.string.widget_filter_show_done), showDone) { showDone = !showDone }
                    Option(stringResource(R.string.widget_filter_match_all), matchAll) { matchAll = !matchAll }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.widget_filter_limit), Modifier.weight(1f))
                        TextButton(onClick = { limit = (limit - 1).coerceAtLeast(1) }) { Text("−") }
                        Text(limit.toString())
                        TextButton(onClick = { limit = (limit + 1).coerceAtMost(WidgetFilter.MAX_LIMIT) }) { Text("+") }
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.widget_content_options), style = MaterialTheme.typography.titleSmall)
                Option(stringResource(R.string.widget_tasks_only), !showNotes) { showNotes = !showNotes }
                Option(stringResource(R.string.widget_expand_default), expandSubtasks) { expandSubtasks = !expandSubtasks }
                Option(stringResource(R.string.widget_show_done_subtasks), showDoneSubtasks) { showDoneSubtasks = !showDoneSubtasks }
                Option(stringResource(R.string.widget_compact), compact) { compact = !compact }
                Text(stringResource(R.string.widget_sort), style = MaterialTheme.typography.titleSmall)
                WidgetSort.entries.forEach { value ->
                    val label = when (value) {
                        WidgetSort.MANUAL -> R.string.widget_sort_manual
                        WidgetSort.DUE_DATE -> R.string.task_deadline
                        WidgetSort.TITLE -> R.string.task_title_field
                    }
                    Option(stringResource(label), sort == value.name, radio = true) { sort = value.name }
                }
                Text(stringResource(R.string.settings_section_appearance), style = MaterialTheme.typography.titleSmall)
                WidgetTheme.entries.forEach { value ->
                    val label = when (value) {
                        WidgetTheme.DARK -> R.string.settings_appearance_dark
                        WidgetTheme.LIGHT -> R.string.settings_appearance_light
                        WidgetTheme.SYSTEM -> R.string.settings_appearance_system
                    }
                    Option(stringResource(label), theme == value.name, radio = true) { theme = value.name }
                }
                TextButton(onClick = { onSave(WidgetFilter(), true) }) { Text(stringResource(R.string.widget_filter_clear)) }
            }
        },
        confirmButton = { TextButton(enabled = !single || tasks.any { it.id == taskId }, onClick = {
            onSave(WidgetFilter(title.trim(), tags, showDone, limit, matchAll, if (single) taskId else "",
                showNotes, expandSubtasks, showDoneSubtasks, compact, WidgetSort.valueOf(sort), WidgetTheme.valueOf(theme)), false)
        }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun Option(label: String, checked: Boolean, radio: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        if (radio) RadioButton(checked, onClick = onClick) else Checkbox(checked, onCheckedChange = { onClick() })
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
