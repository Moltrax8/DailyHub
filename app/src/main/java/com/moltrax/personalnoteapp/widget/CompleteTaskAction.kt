package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Widget'taki checkbox'a tıklandığında çalışır: ilgili görevi tamamlar ve widget'ı yeniler.
 * Böylece uygulamayı açmadan görevler işaretlenebilir.
 *
 * Akış, "anında tepki" için bilinçli olarak şu sırada: önce yerel tamamlama (hızlı DB yazısı) ve
 * geri al bilgisinin bu widget'a yazılması, ardından [TaskWidget.requestUpdate] ile ANINDA görsel
 * yenileme; ağ senkronizasyonu ([TaskWidget.pushSync]) en sona bırakılır — böylece eski "birkaç
 * saniye bekleyip tamamlanma" gecikmesi (sync'in tamamlamayı bloklaması) ortadan kalkar.
 */
class CompleteTaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[taskIdKey] ?: return
        // 1) Yerel tamamlama; tamamlamadan önceki görevi geri al için döndürür.
        val snapshot = TaskWidget.completeTask(context, taskId) ?: return
        // 2) Geri al anlık görüntüsünü SADECE bu widget örneğine yaz (şerit burada belirir).
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[TaskWidget.UNDO_TASK_TITLE] = snapshot.title
            prefs[TaskWidget.UNDO_TASK_JSON] = TaskWidget.encodeUndo(snapshot)
        }
        // 3) Anında görsel yenileme (aynı görev birden çok widget'ta olabilir → tümü).
        TaskWidget.requestUpdate(context)
        // 4) Ağ senkronizasyonu en sonda; arayüz zaten güncellendi.
        TaskWidget.pushSync(context)
    }

    companion object {
        val taskIdKey = ActionParameters.Key<String>("task_id")
    }
}
