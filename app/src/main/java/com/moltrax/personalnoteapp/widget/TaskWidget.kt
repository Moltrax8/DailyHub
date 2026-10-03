package com.moltrax.personalnoteapp.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.moltrax.personalnoteapp.MainActivity
import com.moltrax.personalnoteapp.R
import com.moltrax.personalnoteapp.data.local.preferences.AppPreferences
import com.moltrax.personalnoteapp.data.remote.drive.model.TaskJson
import com.moltrax.personalnoteapp.data.remote.drive.model.toDomain
import com.moltrax.personalnoteapp.data.remote.drive.model.toJson
import com.moltrax.personalnoteapp.domain.model.Task
import com.moltrax.personalnoteapp.domain.model.withCompletion
import com.moltrax.personalnoteapp.domain.repository.SyncRepository
import com.moltrax.personalnoteapp.domain.repository.TaskRepository
import com.moltrax.personalnoteapp.service.NotificationScheduler
import com.moltrax.personalnoteapp.service.NotificationService
import com.moltrax.personalnoteapp.ui.i18n.localizedFor
import com.moltrax.personalnoteapp.ui.theme.AppColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Instead of fixed-size grids, SizeMode.Exact: gives the launcher widget's ACTUAL size,
    // so we adapt continuously (adaptive) to every size from 3x3 up to 4x4/4x5.
    override val sizeMode = SizeMode.Exact

    /**
     * Since the Glance widget is not a Hilt component, we obtain dependencies via
     * EntryPoint through the application context.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TaskWidgetEntryPoint {
        fun taskRepository(): TaskRepository
        fun syncRepository(): SyncRepository
        fun notificationService(): NotificationService
        fun notificationScheduler(): NotificationScheduler
        fun appPreferences(): AppPreferences
    }

    /** A single subtask (checklist) row in the widget; tapping toggles its completion state. */
    private data class SubItem(val id: String, val title: String, val isDone: Boolean)

    private data class TaskItem(
        val id: String,
        val title: String,
        val notes: String?,
        // True if linked to a workout/program: checking does not complete directly, it opens the set/weight screen in the app.
        val isWorkout: Boolean,
        // Checklist items under the task (embedded), also listed in the widget.
        val subtasks: List<SubItem>,
    )

    /** Fixed strings shown in the widget, resolved for the selected language (widget is not Compose). */
    private data class WidgetStrings(val title: String, val error: String, val empty: String, val undo: String)

    private sealed interface UiState {
        data class Content(val tasks: List<TaskItem>) : UiState
        data object Error : UiState
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load data before composition, catching errors; so the widget never gets stuck in infinite
        // loading — at worst it shows an error message.
        val state = loadState(context)
        // Resolve appWidgetId from glanceId so the 'Settings' button can open the config screen for this specific widget.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // Resolve strings for the selected language (the widget cannot use the LocalContext provider).
        val lang = runCatching { entryPoint(context).appPreferences().language.first() }.getOrDefault("en")
        val lc = context.localizedFor(lang)
        val strings = WidgetStrings(
            title = lc.getString(R.string.home_title),
            error = lc.getString(R.string.widget_error),
            empty = lc.getString(R.string.widget_empty),
            undo = lc.getString(R.string.undo),
        )
        provideContent {
            GlanceTheme { WidgetRoot(context, state, appWidgetId, strings) }
        }
    }

    private suspend fun loadState(context: Context): UiState =
        runCatching {
            val items = entryPoint(context).taskRepository().observeAll().first()
                .filter { !it.isDone }
                .map { task ->
                    TaskItem(
                        id = task.id,
                        title = task.title,
                        notes = task.notes,
                        isWorkout = task.linkedWorkoutId != null || task.linkedProgramId != null,
                        subtasks = task.subtasks.map { SubItem(it.id, it.title, it.isDone) },
                    )
                }
            UiState.Content(items)
        }.getOrElse { UiState.Error }

    @Composable
    private fun WidgetRoot(context: Context, state: UiState, appWidgetId: Int, strings: WidgetStrings) {
        val size = LocalSize.current
        // Show a single task only in really small placements; every other size
        // (medium, large, 4x4, 4x5...) fits the full scrollable list.
        val compact = size.width < COMPACT_WIDTH || size.height < COMPACT_HEIGHT
        val prefs = currentState<Preferences>()
        val selectedIds = prefs[SELECTED_TASK_IDS]
        // Title of the last completed task (for the undo strip); without it the strip is hidden.
        val undoTitle = prefs[UNDO_TASK_TITLE]

        // Outer container: dark background + soft corners (modern dark/neon card-feel theme).
        Column(
            modifier = GlanceModifier.fillMaxSize()
                .background(ColorProvider(WidgetColors.Bg))
                .cornerRadius(24.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Header(context, appWidgetId, strings.title)
            Spacer(GlanceModifier.height(10.dp))

            // Content fills the remaining space; if the undo strip exists it stays pinned at the bottom (the list does not shift).
            Box(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                when (state) {
                    is UiState.Error -> Message(strings.error, AppColors.Error)
                    is UiState.Content -> {
                        // If task IDs are selected for this widget, show ONLY those; otherwise
                        // (default behavior) show all tasks. The filter applies at all sizes.
                        val visible =
                            if (selectedIds.isNullOrEmpty()) state.tasks
                            else state.tasks.filter { it.id in selectedIds }
                        when {
                            visible.isEmpty() -> Message(strings.empty, AppColors.TextSecondary)
                            compact -> CompactList(context, visible, strings.empty)
                            else -> FullList(context, visible)   // large/extra-large: all, scrollable
                        }
                    }
                }
            }

            // Undo strip: restores the last completed task with a single tap. It appears on completion
            // and disappears via "Undo"/"✕" or on refresh.
            if (!undoTitle.isNullOrBlank()) {
                Spacer(GlanceModifier.height(8.dp))
                UndoBar(undoTitle, strings.undo)
            }
        }
    }

    /**
     * Bottom undo strip: "✓ <title>" on the left, neon "Undo" button and plain "✕" close
     * chip on the right. Undo runs [UndoTaskAction], ✕ runs [DismissUndoAction].
     */
    @Composable
    private fun UndoBar(title: String, undoLabel: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .cornerRadius(12.dp)
                .background(ColorProvider(WidgetColors.Chip))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓ $title",
                style = TextStyle(color = ColorProvider(AppColors.TextSecondary), fontSize = 12.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Box(
                modifier = GlanceModifier.cornerRadius(8.dp)
                    .background(ColorProvider(AppColors.Accent))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
                    .clickable(actionRunCallback<UndoTaskAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = undoLabel,
                    style = TextStyle(
                        color = ColorProvider(AppColors.TextPrimary),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier.size(28.dp).cornerRadius(8.dp)
                    .background(ColorProvider(WidgetColors.Card))
                    .clickable(actionRunCallback<DismissUndoAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = TextStyle(color = ColorProvider(AppColors.TextSecondary), fontSize = 13.sp),
                )
            }
        }
    }

    /**
     * Header bar: title on the left, three equal-size icon buttons on the right. All items are
     * vertically centered in a fixed-height ([HEADER_HEIGHT]) Row; the title fills the remaining
     * space via [defaultWeight], so buttons stay right-aligned and evenly spaced at every widget width.
     */
    @Composable
    private fun Header(context: Context, appWidgetId: Int, title: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(HEADER_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    color = ColorProvider(AppColors.TextPrimary),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            // Button group: Settings (gear), Refresh (circular arrow), Add (+). Equal spacing between them.
            IconButton("⚙", actionStartActivity(configIntent(context, appWidgetId)))
            Spacer(GlanceModifier.width(BTN_GAP))
            IconButton("↻", actionRunCallback<RefreshTaskWidgetAction>())
            Spacer(GlanceModifier.width(BTN_GAP))
            IconButton("＋", actionStartActivity(newTaskIntent(context)), filled = true)
        }
    }

    /**
     * Icon button in the header — square, rounded-corner chip. If [filled] is true, a neon-filled
     * (accent) button (for '+'); the others are a plain chip background with a neon symbol. Since all share the same [BTN_SIZE]
     * they align perfectly in the header.
     */
    @Composable
    private fun IconButton(glyph: String, onClick: androidx.glance.action.Action, filled: Boolean = false) {
        Box(
            modifier = GlanceModifier.size(BTN_SIZE).cornerRadius(10.dp)
                .background(ColorProvider(if (filled) AppColors.Accent else WidgetColors.Chip))
                .clickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyph,
                style = TextStyle(
                    color = ColorProvider(if (filled) AppColors.TextPrimary else AppColors.Accent),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @Composable
    private fun FullList(context: Context, tasks: List<TaskItem>) {
        LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
            items(tasks) { item ->
                Box(modifier = GlanceModifier.padding(bottom = 8.dp)) { TaskRow(context, item) }
            }
        }
    }

    @Composable
    private fun CompactList(context: Context, tasks: List<TaskItem>, emptyText: String) {
        // In very small placements, show only the first task (of the filtered list).
        val item = tasks.firstOrNull()
        if (item == null) Message(emptyText, AppColors.TextSecondary)
        else TaskRow(context, item)
    }

    /**
     * Card-style task row: clickable checkbox + bold title + faded note below. All items
     * are vertically centered in a single Row; checkbox fixed on the left, text column fills the remaining space via [defaultWeight].
     * This keeps the title and checkbox always aligned on the same axis (the fixed-height
     * decorative bar was removed — it was the actual source of misalignment). Dark card background + soft corners.
     */
    @Composable
    private fun TaskRow(context: Context, item: TaskItem) {
        // For workout-linked tasks, checking does not complete directly: it opens the set/rep/weight screen in the app.
        // Other tasks complete instantly from the widget as usual.
        val checkAction: androidx.glance.action.Action =
            if (item.isWorkout) actionStartActivity(workoutCompleteIntent(context, item.id))
            else actionRunCallback<CompleteTaskAction>(
                actionParametersOf(CompleteTaskAction.taskIdKey to item.id),
            )
        Row(
            modifier = GlanceModifier.fillMaxWidth()
                .cornerRadius(14.dp)
                .background(ColorProvider(WidgetColors.Card))
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckBox(
                checked = false,
                onCheckedChange = checkAction,
            )
            Column(modifier = GlanceModifier.defaultWeight().padding(vertical = 10.dp)) {
                Text(
                    text = item.title,
                    style = TextStyle(
                        color = ColorProvider(AppColors.TextPrimary),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                val note = item.notes
                if (!note.isNullOrBlank()) {
                    Text(
                        text = note,
                        style = TextStyle(
                            color = ColorProvider(AppColors.TextSecondary),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        maxLines = 2,
                        modifier = GlanceModifier.padding(top = 3.dp),
                    )
                }
                // Subtasks (checklist): listed as faded, small lines below the title/note.
                // Completed ones are struck through. Each row is clickable: instantly
                // completes/uncompletes that subtask without opening the app.
                item.subtasks.forEach { sub -> SubtaskRow(item.id, sub) }
            }
        }
    }

    /**
     * A single subtask row: status mark (✓/○) + title (struck through if done). The whole row
     * is clickable; via [ToggleSubtaskAction] it instantly completes/uncompletes the subtask without opening the app.
     */
    @Composable
    private fun SubtaskRow(taskId: String, sub: SubItem) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 3.dp)
                .clickable(
                    actionRunCallback<ToggleSubtaskAction>(
                        actionParametersOf(
                            ToggleSubtaskAction.taskIdKey to taskId,
                            ToggleSubtaskAction.subtaskIdKey to sub.id,
                        ),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (sub.isDone) "✓" else "○",
                style = TextStyle(
                    color = ColorProvider(if (sub.isDone) AppColors.Accent else AppColors.TextSecondary),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(5.dp))
            Text(
                text = sub.title,
                style = TextStyle(
                    color = ColorProvider(AppColors.TextSecondary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    textDecoration = if (sub.isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }

    @Composable
    private fun Message(text: String, color: androidx.compose.ui.graphics.Color) {
        Text(
            text = text,
            style = TextStyle(color = ColorProvider(color)),
            modifier = GlanceModifier.padding(12.dp),
        )
    }

    /** Extra tones specific to the widget (dark/neon complements of the general [AppColors] palette). */
    private object WidgetColors {
        val Bg   = androidx.compose.ui.graphics.Color(0xFF0B0B12) // outer container background
        val Card = androidx.compose.ui.graphics.Color(0xFF1B1B26) // task card background
        val Chip = androidx.compose.ui.graphics.Color(0xFF22222E) // plain icon button background
    }

    companion object {
        // Set of task IDs to show in this widget instance (multi-select). Empty/absent = all.
        val SELECTED_TASK_IDS = stringSetPreferencesKey("selected_task_ids")

        // Undo snapshot of the last completed task — kept only in the tapped widget instance.
        // The JSON carries the task from BEFORE completion; the title is shown in the undo strip.
        val UNDO_TASK_TITLE = stringPreferencesKey("undo_task_title")
        val UNDO_TASK_JSON = stringPreferencesKey("undo_task_json")

        private val json = Json { ignoreUnknownKeys = true }

        // Thresholds below which the single-task (compact) view applies; above switches to the full list.
        private val COMPACT_WIDTH = 200.dp
        private val COMPACT_HEIGHT = 140.dp

        // Header bar metrics — buttons and title are vertically centered at this fixed height.
        private val HEADER_HEIGHT = 40.dp
        private val BTN_SIZE = 38.dp
        private val BTN_GAP = 6.dp

        fun entryPoint(context: Context): TaskWidgetEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                TaskWidgetEntryPoint::class.java,
            )

        private fun newTaskIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("personalnoteapp://new_task")
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_NEW_TASK)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        /**
         * 'Settings' (gear) button: opens this widget's task-selection screen. It launches the transparent
         * [TaskWidgetConfigActivity] in its own task, NOT the main app (MainActivity).
         */
        private fun configIntent(context: Context, appWidgetId: Int): Intent =
            Intent(context, TaskWidgetConfigActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // Unique data per widget → so PendingIntents don't get mixed up.
                data = Uri.parse("personalnoteapp://configure/$appWidgetId")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

        /**
         * When the check of a workout/program-linked task is tapped: instead of completing the task from the widget,
         * it opens the app to the set/rep/weight entry screen (accordion) for that task.
         */
        private fun workoutCompleteIntent(context: Context, taskId: String): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                // Unique data per task → so PendingIntents don't get mixed up.
                data = Uri.parse("personalnoteapp://complete_workout/$taskId")
                putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_COMPLETE_WORKOUT)
                putExtra(MainActivity.EXTRA_WIDGET_TASK_ID, taskId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        suspend fun requestUpdate(context: Context) {
            TaskWidget().updateAll(context)
        }

        /**
         * Completes a task via the widget; mirrors the in-app behavior exactly. Does NOT perform network
         * synchronization HERE (to avoid blocking the UI) — the caller first refreshes instantly via [requestUpdate],
         * then pushes via [pushSync]. Returns the task from BEFORE completion for undo (null = task not found).
         */
        suspend fun completeTask(context: Context, taskId: String): Task? {
            val ep = entryPoint(context)
            val task = ep.taskRepository().getById(taskId) ?: return null
            val now = System.currentTimeMillis()
            // Recurring tasks advance (stay open), normal tasks close.
            val result = task.withCompletion(now)
            ep.taskRepository().upsert(result)
            ep.notificationScheduler().refresh(result)
            return task
        }

        /**
         * Toggles the completion state of a subtask (checklist item) via the widget.
         * Without opening the app, flips the matching item in the embedded subtask list; tapping the
         * same row again reverts it. Does not complete the parent task (matches in-app behavior exactly).
         * Does not sync over the network here — the caller calls [requestUpdate] first, then [pushSync].
         */
        suspend fun toggleSubtask(context: Context, taskId: String, subtaskId: String) {
            val ep = entryPoint(context)
            val task = ep.taskRepository().getById(taskId) ?: return
            if (task.subtasks.none { it.id == subtaskId }) return
            val updated = task.copy(
                subtasks = task.subtasks.map {
                    if (it.id == subtaskId) it.copy(isDone = !it.isDone) else it
                },
                updatedAt = System.currentTimeMillis(),
            )
            ep.taskRepository().upsert(updated)
        }

        /** Serializes the completed task for the undo strip (its pre-completion state). */
        fun encodeUndo(task: Task): String = json.encodeToString(task.toJson())

        /** Undo: restores the task stored via [encodeUndo] to its previous (pre-completion) state. */
        suspend fun restoreTask(context: Context, taskJson: String) {
            val ep = entryPoint(context)
            val task = runCatching { json.decodeFromString<TaskJson>(taskJson).toDomain() }.getOrNull() ?: return
            val restored = task.copy(updatedAt = System.currentTimeMillis())
            ep.taskRepository().upsert(restored)
            ep.notificationScheduler().refresh(restored)
        }

        /**
         * Runs Drive synchronization (silently swallowed when offline). Called AFTER the UI is updated;
         * so checking responds instantly in the widget while network work continues in the background.
         */
        suspend fun pushSync(context: Context) {
            runCatching { entryPoint(context).syncRepository().pushToDrive() }
        }
    }
}
