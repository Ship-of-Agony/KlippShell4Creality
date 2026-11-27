package com.shipofagony.klippshell4creality

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class KlipperOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var prefs: SharedPreferences

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var pollJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        showOverlay()
        startKlipperDataPolling()
    }

    private fun showOverlay() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_mini_status, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40
            y = 40
        }

        overlayView?.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(launchIntent)
            stopSelf()
        }

        windowManager?.addView(overlayView, params)
    }

    private fun startKlipperDataPolling() {
        val printerListStr = prefs.getString("saved_printers_list", "[]") ?: "[]"
        var printerIp = "127.0.0.1"
        var printerPort = "7125"

        try {
            val jsonArray = JSONArray(printerListStr)
            if (jsonArray.length() > 0) {
                val primaryPrinter = jsonArray.getJSONObject(0)
                printerIp = primaryPrinter.optString("ip", "127.0.0.1")
                printerPort = primaryPrinter.optString("port", "7125")
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Failed to parse printer list in overlay service", e)
        }

        val klipperUrlStr = "http://$printerIp:$printerPort/printer/objects/query?display_status"

        pollJob = serviceScope.launch {
            val tvProgress = overlayView?.findViewById<TextView>(R.id.tvOverlayProgress)
            val progressBar = overlayView?.findViewById<ProgressBar>(R.id.pbOverlayProgress)

            while (isActive) {
                val progressData = fetchKlipperProgress(klipperUrlStr)

                withContext(Dispatchers.Main) {
                    if (progressData != null) {
                        val progressPercent = progressData * 100.0
                        tvProgress?.text = String.format(Locale.getDefault(), "%.1f%%", progressPercent)
                        progressBar?.progress = progressPercent.toInt()
                    } else {
                        tvProgress?.text = "Offline"
                        progressBar?.progress = 0
                    }
                }
                delay(2000)
            }
        }
    }

    private fun fetchKlipperProgress(urlStr: String): Double? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
                useCaches = false
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val stream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(stream))
                val responseText = reader.use { it.readText() }
                val json = JSONObject(responseText)
                val status = json.optJSONObject("result")?.optJSONObject("status")
                val displayStatus = status?.optJSONObject("display_status")

                if (displayStatus != null && displayStatus.has("progress")) {
                    return displayStatus.optDouble("progress", 0.0)
                }
            }
            null
        } catch (e: Exception) {
            Log.e("KlippShell", "Overlay network request failed", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    override fun onDestroy() {
        pollJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
        overlayView?.let {
            windowManager?.removeView(it)
        }
    }
}