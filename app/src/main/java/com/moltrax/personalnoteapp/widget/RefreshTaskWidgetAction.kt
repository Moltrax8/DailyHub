package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * 'Yenile' butonuna tıklandığında çalışır: widget güncelleme tetikleyicisini çalıştırır.
 * updateAll → provideGlance yeniden composition demektir; provideGlance her seferinde veriyi
 * repository'den taze çektiği için liste anında güncel haline yenilenir. Bekleyen geri al şeridi
 * de yenilemede temizlenir (eskimiş bir geri al kalmasın).
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
