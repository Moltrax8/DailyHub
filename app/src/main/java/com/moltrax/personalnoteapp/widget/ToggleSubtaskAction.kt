package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Runs when a subtask (checklist item) in the widget is tapped: flips that subtask's completion
 * state and refreshes the widget. Subtasks can thus be checked/unchecked directly on the widget
 * without opening the app. Tapping the same row again reverts the action.
 *
 * Follows the same "instant response" order as [CompleteTaskAction]: first a fast local DB write, then
 * INSTANT visual refresh via [TaskWidget.requestUpdate], with network sync ([TaskWidget.pushSync])
 * left to the end — so the check appears without delay while sync continues in the background.
 */
class ToggleSubtaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[taskIdKey] ?: return
        val subtaskId = parameters[subtaskIdKey] ?: return
        // 1) Local toggle (fast DB write).
        TaskWidget.toggleSubtask(context, taskId, subtaskId)
        // 2) Instant visual refresh (the same task may be in multiple widgets → all).
        TaskWidget.requestUpdate(context)
        // 3) Network sync last; the UI is already updated.
        TaskWidget.pushSync(context)
    }

    companion object {
        val taskIdKey = ActionParameters.Key<String>("task_id")
        val subtaskIdKey = ActionParameters.Key<String>("subtask_id")
    }
}
