package com.moltrax.personalnoteapp.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import com.moltrax.personalnoteapp.widget.TaskWidget
import com.moltrax.personalnoteapp.widget.clearTaskUndo

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
            p.clearTaskUndo()
        }
        TaskWidget.requestUpdate(context)
    }
}
