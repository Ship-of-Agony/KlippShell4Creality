package com.shipofagony.klippshell4creality

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import org.json.JSONArray

class RemoteWidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immer mit "CANCELED" starten, falls der User den Dialog abbricht
        setResult(RESULT_CANCELED)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Dynamischen Auswahldialog im KlippShell-Stil aufbauen
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("printers_list", "[]") ?: "[]"
        val printerArray = try { JSONArray(jsonStr) } catch (e: Exception) { JSONArray() }

        if (printerArray.length() == 0) {
            showNoPrintersToast()
            finish()
            return
        }

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val paddingPx = (16 * resources.displayMetrics.density).toInt()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
            setBackgroundColor(if (isNight) Color.parseColor("#252B2E") else Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = TextView(this).apply {
            text = getString(R.string.widget_remote_config_title)
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * resources.displayMetrics.density).toInt())
        }
        rootLayout.addView(tvTitle)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (200 * resources.displayMetrics.density).toInt())
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(container)
        rootLayout.addView(scrollView)

        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        for (i in 0 until printerArray.length()) {
            val obj = printerArray.getJSONObject(i)
            val name = obj.optString("name", getString(R.string.widget_printer_unknown))
            val ip = obj.optString("ip", "")

            val btn = MaterialButton(this).apply {
                text = name
                isAllCaps = false
                textSize = 16f
                isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, (14 * resources.displayMetrics.density).toInt())
                backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                setTextColor(defaultTxtColor)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, (6 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
                }

                setOnClickListener {
                    // Auswahl für genau dieses Widget sichern mit lokalisiertem Namensformat
                    val remoteName = getString(R.string.widget_remote_name_format, name)

                    prefs.edit()
                        .putString("widget_remote_ip_$appWidgetId", ip)
                        .putString("widget_remote_name_$appWidgetId", remoteName)
                        // Synchronisiere auch direkt die Master-IP für die normale Remote-App
                        .putString("last_master_tv_ip", ip)
                        .apply()

                    // Widget updaten
                    val appWidgetManager = AppWidgetManager.getInstance(this@RemoteWidgetConfigActivity)
                    RemoteWidgetProvider.updateAppWidget(this@RemoteWidgetConfigActivity, appWidgetManager, appWidgetId)

                    // Erfolg signalisieren
                    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(RESULT_OK, resultValue)
                    finish()
                }
            }
            container.addView(btn)
        }

        setContentView(rootLayout)
    }

    private fun showNoPrintersToast() {
        Toast.makeText(this, getString(R.string.widget_config_no_printers_error), Toast.LENGTH_LONG).show()
    }
}