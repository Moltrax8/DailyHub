package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Runs when the checkbox in the widget is tapped: completes the related task and refreshes the widget.
 * This allows tasks to be checked off without opening the app.
 *
 * Saves locally and refreshes immediately; undo belongs only to the tapped widget.
 */
class CompleteTaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[taskIdKey] ?: return
        // 1) Local completion; returns the pre-completion task for undo.
        val snapshot = TaskWidget.completeTask(context, taskId) ?: return
        // 2) Write the undo snapshot ONLY to this widget instance (the strip appears here).
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[TaskWidget.UNDO_TASK_TITLE] = snapshot.title
            prefs[TaskWidget.UNDO_TASK_JSON] = TaskWidget.encodeUndo(snapshot)
        }
        // 3) Instant visual refresh (the same task may be in multiple widgets → all).
        TaskWidget.requestUpdate(context)
    }

    companion object {
        val taskIdKey = ActionParameters.Key<String>("task_id")
    }
}
