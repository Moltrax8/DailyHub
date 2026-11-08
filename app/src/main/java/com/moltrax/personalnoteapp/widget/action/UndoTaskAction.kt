package com.moltrax.personalnoteapp.widget.action

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.moltrax.personalnoteapp.widget.TaskWidget
import com.moltrax.personalnoteapp.widget.clearTaskUndo

/**
 * Runs when the "Undo" button in the undo strip is pressed: restores the last completed task
 * to its previous state from the snapshot stored in this widget instance, clears the undo info and refreshes.
 */
class UndoTaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val taskJson = prefs[TaskWidget.UNDO_TASK_JSON]
        if (taskJson != null) TaskWidget.restoreTask(context, taskJson)
        // Clear the undo info (the strip disappears).
        updateAppWidgetState(context, glanceId) { p ->
            p.clearTaskUndo()
        }
        TaskWidget.requestUpdate(context)
    }
}
