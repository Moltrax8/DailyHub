package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Runs when the 'Refresh' button is tapped: triggers a widget update.
 * updateAll means re-composition via provideGlance; since provideGlance freshly pulls data
 * from the repository each time, the list is instantly refreshed. Any pending undo strip
 * is also cleared on refresh (so no stale undo remains).
 */
class RefreshTaskWidgetAction : ActionCallback {

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
