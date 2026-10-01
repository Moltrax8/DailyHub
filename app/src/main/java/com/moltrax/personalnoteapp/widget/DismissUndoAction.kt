package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Runs when the "✕" (close) chip in the undo strip is pressed: clears the undo info; the task
 * stays completed. Affects only this widget instance.
 */
class DismissUndoAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { p ->
            p.remove(TaskWidget.UNDO_TASK_TITLE)
            p.remove(TaskWidget.UNDO_TASK_JSON)
        }
        TaskWidget.requestUpdate(context)
    }
}
