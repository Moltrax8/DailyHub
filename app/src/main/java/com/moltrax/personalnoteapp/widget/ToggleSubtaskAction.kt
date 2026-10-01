package com.moltrax.personalnoteapp.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/**
 * Widget'taki bir alt göreve (checklist maddesi) dokununca çalışır: o alt görevin tamamlanma
 * durumunu ters çevirir ve widget'ı yeniler. Böylece uygulamayı açmadan, doğrudan widget üzerinde
 * alt görevler işaretlenip geri alınabilir. Aynı satıra tekrar dokunmak işlemi geri alır.
 *
 * [CompleteTaskAction] ile aynı "anında tepki" sırasını izler: önce hızlı yerel DB yazısı, ardından
 * [TaskWidget.requestUpdate] ile ANINDA görsel yenileme, ağ senkronizasyonu ([TaskWidget.pushSync])
 * en sona bırakılır — böylece tik gecikmesiz görünür, sync arkada sürer.
 */
class ToggleSubtaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[taskIdKey] ?: return
        val subtaskId = parameters[subtaskIdKey] ?: return
        // 1) Yerel toggle (hızlı DB yazısı).
        TaskWidget.toggleSubtask(context, taskId, subtaskId)
        // 2) Anında görsel yenileme (aynı görev birden çok widget'ta olabilir → tümü).
        TaskWidget.requestUpdate(context)
        // 3) Ağ senkronizasyonu en sonda; arayüz zaten güncellendi.
        TaskWidget.pushSync(context)
    }

    companion object {
        val taskIdKey = ActionParameters.Key<String>("task_id")
        val subtaskIdKey = ActionParameters.Key<String>("subtask_id")
    }
}
