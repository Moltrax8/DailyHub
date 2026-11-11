package com.moltrax.personalnoteapp.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.moltrax.personalnoteapp.widget.TaskWidget
import kotlinx.coroutines.delay

/**
 * Runs when a subtask (checklist item) in the widget is tapped: flips that subtask's completion
 * state and refreshes the widget. Subtasks can thus be checked/unchecked directly on the widget
 * without opening the app. Tapping the same row again reverts the action.
 *
 * A newly completed item stays visible with its checkmark for a moment, then the follow-up
 * refresh drops it from the list.
 *
 * Saves locally, then refreshes every widget showing this task.
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
        val becameDone = TaskWidget.toggleSubtask(context, taskId, subtaskId)
        if (becameDone) TaskWidget.markSubtaskLingering(taskId, subtaskId)
        else TaskWidget.clearSubtaskLingering(taskId, subtaskId)
        // 2) Instant visual refresh (the same task may be in multiple widgets → all).
        TaskWidget.requestUpdate(context)
        // 3) Let the checkmark register, then refresh again so the item drops off.
        if (becameDone) {
            delay(TaskWidget.DONE_SUBTASK_LINGER_MS)
            TaskWidget.requestUpdate(context)
        }
    }

    companion object {
        val taskIdKey = ActionParameters.Key<String>("task_id")
        val subtaskIdKey = ActionParameters.Key<String>("subtask_id")
    }
}
