package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/**
 * Geri al şeridindeki "Geri Al" butonuna basılınca çalışır: bu widget örneğinde saklı anlık
 * görüntüden son tamamlanan görevi eski hâline döndürür, geri al bilgisini temizler ve yeniler.
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
        // Geri al bilgisini temizle (şerit kaybolur).
        updateAppWidgetState(context, glanceId) { p ->
            p.remove(TaskWidget.UNDO_TASK_TITLE)
            p.remove(TaskWidget.UNDO_TASK_JSON)
        }
        TaskWidget.requestUpdate(context)
        TaskWidget.pushSync(context)
    }
}
