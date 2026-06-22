package com.shipofagony.klippshell4creality

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class RemoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (appWidgetId in appWidgetIds) {
            editor.remove("widget_remote_ip_$appWidgetId")
            editor.remove("widget_remote_name_$appWidgetId")
        }
        editor.apply()
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

            // Standardwert durch übersetzten Platzhalter ausgetauscht
            val defaultTitle = context.getString(R.string.widget_remote_default_title)
            val printerName = prefs.getString("widget_remote_name_$appWidgetId", defaultTitle)
            val printerIp = prefs.getString("widget_remote_ip_$appWidgetId", "")

            val views = RemoteViews(context.packageName, R.layout.widget_remote_layout)
            views.setTextViewText(R.id.tvWidgetRemoteTitle, printerName)

            // Intent zur direkten Aktivierung der CompanionRemoteActivity
            val intent = Intent(context, CompanionRemoteActivity::class.java).apply {
                putExtra("TARGET_WIDGET_IP", printerIp)
                // Flag setzen, damit die Remote Bescheid weiß, welche IP erzwungen werden soll
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId, // Einzigartiger RequestCode pro Widget-Instanz
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRemoteContainer, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}