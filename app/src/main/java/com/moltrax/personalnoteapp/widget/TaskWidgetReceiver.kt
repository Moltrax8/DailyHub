package com.moltrax.personalnoteapp.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Data loading now happens inside [TaskWidget.provideGlance]; so the only thing the receiver
 * has to do is bind the widget. The APPWIDGET_UPDATE broadcast sent by the system is
 * automatically turned into composition (and thus data loading) by the base class.
 */
class TaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TaskWidget()
}
