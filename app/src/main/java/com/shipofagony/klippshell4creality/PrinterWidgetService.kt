package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONArray
import org.json.JSONObject

class PrinterWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = PrinterWidgetFactory(applicationContext)
}

class PrinterWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var printerList = mutableListOf<JSONObject>()

    private fun loadPrintersFromPrefs() {
        printerList.clear()
        val prefs = context.applicationContext.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("printers_list", "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) printerList.add(arr.getJSONObject(i))
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onCreate() { loadPrintersFromPrefs() }
    override fun onDataSetChanged() { loadPrintersFromPrefs() }
    override fun onDestroy() { printerList.clear() }
    override fun getCount(): Int = printerList.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= printerList.size) return RemoteViews(context.packageName, R.layout.widget_printer_item)

        val printer = printerList[position]
        val name = printer.optString("name", context.getString(R.string.widget_printer_unknown))
        val ip = printer.optString("ip", "")
        val port = printer.optString("port", "7125")
        val model = printer.optString("model", "").trim()
        val defaultView = printer.optString("defaultView", "interface")

        val views = RemoteViews(context.packageName, R.layout.widget_printer_item).apply {
            setTextViewText(R.id.tvWidgetPrinterName, name)

            // Show only pure IP address to avoid unwanted line breaks
            setTextViewText(R.id.tvWidgetPrinterIp, ip)

            val imageResId = when (model.lowercase()) {
                "cr-10", "cr10" -> R.drawable.printer_cr_10
                "cr-10 se", "cr10se" -> R.drawable.printer_cr_10se
                "cr-10 smart", "cr10smart" -> R.drawable.printer_cr_10smart
                "cr-10 smart pro", "cr10smartpro" -> R.drawable.printer_cr_10smartpro
                "cr-10s pro v2", "cr10sprov2" -> R.drawable.printer_cr_10sprov2
                "cr-20 pro", "cr20pro" -> R.drawable.printer_cr_20pro
                "cr-30", "cr30" -> R.drawable.printer_cr_30
                "cr-6 se", "cr6se" -> R.drawable.printer_cr_6se
                "cr-m4", "crm4" -> R.drawable.printer_cr_m4
                "cr-m4 se", "crm4se" -> R.drawable.printer_cr_m4se
                "ender-2 pro", "ender2pro" -> R.drawable.printer_ender_2pro
                "ender-3", "ender3" -> R.drawable.printer_ender_3
                "ender-3 max", "ender3max" -> R.drawable.printer_ender_3max
                "ender-3 max neo", "ender3maxneo" -> R.drawable.printer_ender_3maxneo
                "ender-3 neo", "ender3neo" -> R.drawable.printer_ender_3neo
                "ender-3 s1", "ender3s1" -> R.drawable.printer_ender_3s1
                "ender-3 s1 plus", "ender3s1plus" -> R.drawable.printer_ender_3s1plus
                "ender-3 s1 pro", "ender3s1pro" -> R.drawable.printer_ender_3s1pro
                "ender-3 v2", "ender3v2" -> R.drawable.printer_ender_3v2
                "ender-3 v3", "ender3v3" -> R.drawable.printer_ender_3v3
                "ender-3 v3 ke", "ender3v3ke" -> R.drawable.printer_ender_3v3ke
                "ender-3 v3 plus", "ender3v3plus" -> R.drawable.printer_ender_3v3plus
                "ender-3 v3 se", "ender3v3se" -> R.drawable.printer_ender_3v3se
                "ender-3 v4", "ender3v4" -> R.drawable.printer_ender_3v4
                "ender-5 max", "ender5max" -> R.drawable.printer_ender_5max
                "ender-5 plus", "ender5plus" -> R.drawable.printer_ender_5plus
                "ender-5 s1", "ender5s1" -> R.drawable.printer_ender_5s1
                "k1" -> R.drawable.printer_k1
                "k1c", "k1 c" -> R.drawable.printer_k1c
                "k1 max", "k1max" -> R.drawable.printer_k1max
                "k1 se", "k1se" -> R.drawable.printer_k1se
                "k2" -> R.drawable.printer_k2
                "k2 plus", "k2plus" -> R.drawable.printer_k2plus
                "k2 pro", "k2pro" -> R.drawable.printer_k2pro
                "k2 se", "k2se" -> R.drawable.printer_k2se
                "sermoon d3", "sermoond3" -> R.drawable.printer_sermoond3
                "sermoon d3 pro", "sermoond3pro" -> R.drawable.printer_sermoond3pro
                "sermoon m300", "sermoonm300" -> R.drawable.printer_sermoonm300
                "sermoon v1 pro", "sermoonv1pro" -> R.drawable.printer_sermoonv1pro
                "sonic ender-3 s1", "sonic ender3s1" -> R.drawable.printer_sonic_ender_3s1
                "sonic ender-5 s1", "sonic ender5s1" -> R.drawable.printer_sonic_ender_5s1
                "gs-01", "gs01" -> R.drawable.printer_gs_01
                "gs-02", "gs02" -> R.drawable.printer_gs_02
                "gs-03", "gs03" -> R.drawable.printer_gs_03
                "gs-04", "gs04" -> R.drawable.printer_gs_04
                "hi" -> R.drawable.printer_hi
                "sparkxi7", "sparkx i7" -> R.drawable.printer_sparkxi7
                else -> R.drawable.printer_k2plus
            }
            setImageViewResource(R.id.ivWidgetPrinterIcon, imageResId)
        }

        // Port and IP are passed unmodified in the intent to ensure WebViewActivity loads properly
        val fillInIntent = Intent().apply {
            putExtras(Bundle().apply {
                putString("PRINTER_IP", ip)
                putString("PRINTER_PORT", port)
                putBoolean("IS_CAMERA_VIEW", defaultView == "camera")
            })
        }
        views.setOnClickFillInIntent(R.id.widgetItemContainer, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}