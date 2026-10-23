package com.moltrax.personalnoteapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.*
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.layout.*
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.*
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.glance.unit.ColorProvider
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.withCompletion
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Local snapshot, independent of any backup or network format. */
@Serializable
internal data class WidgetUndoSnapshot(val task: Task)

private data class WidgetPalette(val bg: Color, val card: Color, val chip: Color, val text: Color, val secondary: Color, val accent: Color)
private val DarkPalette = WidgetPalette(Color(0xFF111318), Color(0xFF20242E), Color(0xFF262B37), Color(0xFFF2F3F7), Color(0xFFB7BFCE), Color(0xFFB5C8FF))
private val LightPalette = WidgetPalette(Color(0xFFF2F4F9), Color.White, Color(0xFFE3E8F2), Color(0xFF192131), Color(0xFF526078), Color(0xFF315BB5))
private val LocalWidgetPalette = staticCompositionLocalOf { DarkPalette }

class TaskWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskWidgetEntryPoint {
        fun taskRepository(): TaskRepository
        fun appPreferences(): AppPreferences
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = entryPoint(context).taskRepository()
        val result = try { Result.success(repository.getAll()) } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { Result.failure(e) }
        val language = entryPoint(context).appPreferences().language.first()
        val localized = context.localizedFor(language)
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            GlanceTheme {
                val state = currentState<Preferences>()
                val filter = WidgetFilter.load(state)
                val dark = when (filter.theme) {
                    WidgetTheme.DARK -> true
                    WidgetTheme.LIGHT -> false
                    WidgetTheme.SYSTEM -> context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                CompositionLocalProvider(LocalWidgetPalette provides if (dark) DarkPalette else LightPalette) {
                val colors = LocalWidgetPalette.current
                val narrow = LocalSize.current.width < 240.dp
                val allTasks by repository.observeAll().collectAsState(initial = result.getOrDefault(emptyList()))
                val legacyIds = state[SELECTED_TASK_IDS].orEmpty()
                val visible = if (filter.taskId.isBlank() && legacyIds.isNotEmpty())
                    allTasks.filter { it.id in legacyIds && (filter.showDone || !it.isDone) }
                else filter.select(allTasks)
                val expanded = state[EXPANDED_TASK_IDS].orEmpty()
                Column(GlanceModifier.fillMaxSize().background(ColorProvider(colors.bg)).cornerRadius(24.dp).padding(if (filter.compact) 8.dp else 12.dp)) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(filter.title.ifBlank { localized.getString(R.string.home_title) },
                            modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                            style = TextStyle(color = ColorProvider(colors.text), fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        HeaderButton("⚙", localized.getString(R.string.widget_config_title), actionStartActivity(Intent(context, TaskWidgetConfigActivity::class.java).apply {
                            data = Uri.parse("personalnoteapp://configure/$widgetId")
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }))
                        if (!narrow) HeaderButton("↻", localized.getString(R.string.action_refresh), actionRunCallback<RefreshTaskWidgetAction>())
                        HeaderButton("＋", localized.getString(R.string.home_new_task), actionStartActivity(Intent(context, MainActivity::class.java).apply {
                            data = Uri.parse("personalnoteapp://new_task")
                            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_NEW_TASK)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }))
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        if (result.isFailure) item {
                            Box(GlanceModifier.clickable(actionRunCallback<RefreshTaskWidgetAction>())) {
                                Message(localized.getString(R.string.widget_error))
                            }
                        }
                        else if (visible.isEmpty()) item {
                            Message(localized.getString(if (filter.taskId.isBlank()) R.string.widget_empty else R.string.widget_task_missing))
                        }
                        else visible.forEach { task ->
                            item {
                                TaskRow(task, filter.expandSubtasks.xor(task.id in expanded), filter,
                                    localized.getString(R.string.widget_subtasks_remaining, task.subtaskCount - task.doneSubtaskCount),
                                    localized.getString(R.string.widget_open_task),
                                    actionStartActivity(Intent(context, MainActivity::class.java).apply {
                                        data = Uri.parse("personalnoteapp://reminder_tap/${Uri.encode(task.id)}")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }))
                            }
                            // Separate lazy rows keep long checklists scrollable rather than clipping one giant card.
                            if (filter.expandSubtasks.xor(task.id in expanded)) task.subtasks.filter { filter.showDoneSubtasks || !it.isDone }.forEach { sub ->
                                item {
                                    Row(GlanceModifier.fillMaxWidth().padding(start = 18.dp, bottom = 4.dp)
                                        .background(ColorProvider(colors.card)).cornerRadius(8.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        CheckBox(checked = sub.isDone, text = sub.title,
                                            maxLines = if (filter.compact) 1 else 3,
                                            style = TextStyle(color = ColorProvider(if (sub.isDone) colors.secondary else colors.text), fontSize = 13.sp,
                                                textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None),
                                            onCheckedChange = actionRunCallback<ToggleSubtaskAction>(actionParametersOf(
                                                ToggleSubtaskAction.taskIdKey to task.id,
                                                ToggleSubtaskAction.subtaskIdKey to sub.id)))
                                    }
                                }
                            }
                        }
                    }
                    state[UNDO_TASK_TITLE]?.let { title ->
                        Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                                style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 12.sp))
                            Text(localized.getString(R.string.undo), modifier = GlanceModifier.padding(8.dp)
                                .clickable(actionRunCallback<UndoTaskAction>()),
                                style = TextStyle(color = ColorProvider(colors.accent), fontSize = 13.sp))
                            HeaderButton("×", localized.getString(R.string.action_dismiss), actionRunCallback<DismissUndoAction>())
                        }
                    }
                }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(task: Task, expanded: Boolean, filter: WidgetFilter, remaining: String, openLabel: String, open: androidx.glance.action.Action) {
        val colors = LocalWidgetPalette.current
        Column(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).cornerRadius(14.dp).background(ColorProvider(colors.card))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CheckBox(checked = task.isDone, onCheckedChange = actionRunCallback<CompleteTaskAction>(
                    actionParametersOf(CompleteTaskAction.taskIdKey to task.id)))
                val toggle = actionRunCallback<ToggleTaskExpansionAction>(actionParametersOf(
                    ToggleTaskExpansionAction.taskIdKey to task.id))
                Column(GlanceModifier.defaultWeight().padding(vertical = if (filter.compact) 6.dp else 10.dp, horizontal = 4.dp)
                    .clickable(if (task.subtasks.isEmpty()) open else toggle)) {
                    Text(task.title, maxLines = if (filter.compact) 1 else 2, style = TextStyle(color = ColorProvider(colors.text),
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None))
                    if (task.subtasks.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${task.doneSubtaskCount}/${task.subtaskCount}", modifier = GlanceModifier
                                .cornerRadius(8.dp).background(ColorProvider(colors.chip)).padding(horizontal = 8.dp, vertical = 4.dp),
                                style = TextStyle(color = ColorProvider(colors.accent), fontSize = 12.sp))
                            Text("  $remaining  ${if (expanded) "▴" else "▾"}", maxLines = 1,
                                style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 12.sp))
                        }
                    }
                    if (filter.showNotes && !task.notes.isNullOrBlank()) Text(task.notes, maxLines = if (filter.compact) 1 else 3,
                        style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 12.sp))
                }
            }
            if (expanded) Text(openLabel, modifier = GlanceModifier.padding(start = 48.dp, bottom = 10.dp)
                .clickable(open), style = TextStyle(color = ColorProvider(colors.accent), fontSize = 12.sp))
        }
    }

    @Composable
    private fun HeaderButton(label: String, description: String, action: androidx.glance.action.Action) {
        val colors = LocalWidgetPalette.current
        Box(GlanceModifier.padding(start = 4.dp).size(40.dp).cornerRadius(10.dp)
            .background(ColorProvider(colors.chip)).clickable(action).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = ColorProvider(colors.accent), fontSize = 18.sp))
        }
    }

    @Composable
    private fun Message(text: String) {
        Text(text, modifier = GlanceModifier.padding(12.dp), style = TextStyle(color = ColorProvider(LocalWidgetPalette.current.secondary)))
    }

    companion object {
        val SELECTED_TASK_IDS = stringSetPreferencesKey("selected_task_ids")
        val EXPANDED_TASK_IDS = stringSetPreferencesKey("expanded_task_ids")
        val UNDO_TASK_TITLE = stringPreferencesKey("undo_task_title")
        val UNDO_TASK_JSON = stringPreferencesKey("undo_task_json")
        private val json = Json { ignoreUnknownKeys = true }

        fun entryPoint(context: Context): TaskWidgetEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, TaskWidgetEntryPoint::class.java)

        suspend fun requestUpdate(context: Context) { TaskWidget().updateAll(context) }

        suspend fun completeTask(context: Context, taskId: String): Task? {
            val repository = entryPoint(context).taskRepository()
            val task = repository.getById(taskId) ?: return null
            repository.upsert(if (task.isDone) task.copy(isDone = false, completedAt = null, updatedAt = System.currentTimeMillis())
                else task.withCompletion())
            return task
        }

        suspend fun toggleSubtask(context: Context, taskId: String, subtaskId: String) {
            val repository = entryPoint(context).taskRepository()
            val task = repository.getById(taskId) ?: return
            if (task.subtasks.none { it.id == subtaskId }) return
            repository.upsert(task.copy(updatedAt = System.currentTimeMillis(),
                subtasks = task.subtasks.map { if (it.id == subtaskId) it.copy(isDone = !it.isDone) else it }))
        }

        fun encodeUndo(task: Task): String = json.encodeToString(WidgetUndoSnapshot(task))

        suspend fun restoreTask(context: Context, taskJson: String) {
            val task = runCatching { json.decodeFromString<WidgetUndoSnapshot>(taskJson).task }.getOrNull() ?: return
            val repository = entryPoint(context).taskRepository()
            // A stale widget snapshot must never resurrect a task deleted in the app.
            val current = repository.getById(task.id) ?: return
            repository.upsert(current.copy(isDone = task.isDone, completedAt = task.completedAt,
                dueDate = task.dueDate, updatedAt = System.currentTimeMillis()))
        }
    }
}
