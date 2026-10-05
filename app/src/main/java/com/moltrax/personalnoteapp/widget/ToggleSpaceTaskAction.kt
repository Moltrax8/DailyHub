package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.flow.first

/**
 * Toggles a shared hub/project todo from the widget (repo-wise widgets).
 * Server is truth via [com.moltrax.personalnoteapp.domain.repository.SpaceRepository];
 * the widget refreshes right after the write.
 */
class ToggleSpaceTaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val spaceId = parameters[spaceIdKey]?.takeIf { it.isNotBlank() } ?: return
        val taskId = parameters[taskIdKey] ?: return
        val spaces = TaskWidget.entryPoint(context).spaceRepository()
        val task = runCatching { spaces.observeTasks(spaceId).first().firstOrNull { it.id == taskId } }
            .getOrNull() ?: return
        runCatching { spaces.toggleSharedTask(task) }
        TaskWidget.requestUpdate(context)
    }

    companion object {
        val spaceIdKey = ActionParameters.Key<String>("space_id")
        val taskIdKey = ActionParameters.Key<String>("task_id")
    }
}
