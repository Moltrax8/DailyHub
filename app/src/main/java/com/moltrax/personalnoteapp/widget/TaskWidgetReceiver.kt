package com.moltrax.personalnoteapp.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Veri yükleme artık [TaskWidget.provideGlance] içinde yapılıyor; bu yüzden receiver'ın yapması
 * gereken tek şey widget'ı bağlamak. Sistemin gönderdiği APPWIDGET_UPDATE yayını taban sınıf
 * tarafından otomatik olarak composition'a (ve dolayısıyla veri yüklemeye) dönüştürülür.
 */
class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}
