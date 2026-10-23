package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

class ToggleTaskExpansionAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[taskIdKey] ?: return
        updateAppWidgetState(context, glanceId) { prefs ->
            val ids = prefs[TaskWidget.EXPANDED_TASK_IDS].orEmpty()
            prefs[TaskWidget.EXPANDED_TASK_IDS] = if (taskId in ids) ids - taskId else ids + taskId
        }
        TaskWidget().update(context, glanceId)
    }
    companion object { val taskIdKey = ActionParameters.Key<String>("task_id") }
}
