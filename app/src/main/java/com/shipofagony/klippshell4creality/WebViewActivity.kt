package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@Suppress("DEPRECATION", "Lint", "ClickableViewAccessibility", "SetJavaScriptEnabled", "SetTextI18n", "LocalSuppress")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "SetTextI18n", "DefaultLocale", "NewApi", "MissingSuperCall")
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutOsd: View
    private lateinit var layoutWebButtons: LinearLayout

    private var currentActiveUrl: String = ""
    private var isCameraMode: Boolean = false
    private var isOsdEnabled: Boolean = false

    // Start-Konfiguration für Klipper-Bauraumsensoren
    private var knownChamberSensor: String? = "temperature_sensor chamber_temp"
    private var knownChamberHeater: String? = "heater_generic chamber_heater"
    private var cachedChamberQueryString: String = "&temperature_sensor%20chamber_temp&heater_generic%20chamber_heater"
    private var chamberSearchIndex = 0

    private val chamberSensorsToTry = listOf(
        "temperature_sensor chamber_temp", "temperature_sensor chamber",
        "temperature_sensor enclosure_temp", "temperature_sensor enclosure"
    )
    private val chamberHeatersToTry = listOf(
        "heater_generic chamber_heater", "heater_generic chamber",
        "heater_generic enclosure_heater", "enclosure_heater"
    )

    // SMART-NOTIFY ENGINE: Interne Trigger-Flags gegen Dauerfeuer
    private var hasTrigFirstLayer = false
    private var hasTrig50 = false
    private var hasTrig75 = false
    private var hasTrig90 = false
    private var hasTrig100 = false
    private var hasTrigOffline = false

    private var lastPrintState: String = ""
    private val osdHandler = Handler(Looper.getMainLooper())
    private val osdRunnable = object : Runnable {
        override fun run() {
            if (isOsdEnabled && isCameraMode) {
                fetchMoonrakerData()
                osdHandler.postDelayed(this, 3000)
            }
        }
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideUiRunnable = Runnable { hideButtons() }
    private val immersiveTimeout = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webView)
        layoutOsd = findViewById(R.id.layoutOsd)
        layoutWebButtons = findViewById(R.id.layoutWebButtons)

        val btnMenu = findViewById<MaterialButton>(R.id.btnWebMenu)
        val btnToggle = findViewById<MaterialButton>(R.id.btnWebToggle)
        val btnClose = findViewById<MaterialButton>(R.id.btnWebClose)

        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) showButtons()
            false
        }

        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                showButtons()
                view.animate().scaleX(1.1f).scaleY(1.1f).alpha(1.0f).translationZ(8f).setDuration(150).start()
                if (view is MaterialButton) {
                    view.strokeWidth = 6
                    view.strokeColor = ColorStateList.valueOf(Color.WHITE)
                }
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.8f).translationZ(0f).setDuration(150).start()
                if (view is MaterialButton) view.strokeWidth = 0
            }
        }

        arrayOf(btnMenu, btnToggle, btnClose).forEach { btn ->
            btn.isFocusable = true
            btn.alpha = 0.8f
            btn.onFocusChangeListener = tvFocusListener
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        currentActiveUrl = intent.getStringExtra("TARGET_URL") ?: "http://google.com"
        val hostIp = Uri.parse(currentActiveUrl).host ?: ""
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedRatio = try { prefs.getFloat("camera_ratio_$hostIp", 56.25f) } catch(e: Exception) { 56.25f }

        isOsdEnabled = prefs.getBoolean("osd_enabled_$hostIp", false)
        loadStreamOrUrl(currentActiveUrl, savedRatio)
        showButtons()

        btnClose.setOnClickListener { finish() }
    }

    override fun onStop() {
        super.onStop()
        osdHandler.removeCallbacks(osdRunnable)
        uiHandler.removeCallbacks(hideUiRunnable)
        SoundManager.stopAllSounds()
    }

    private fun showButtons() {
        if (layoutWebButtons.alpha < 1f) {
            layoutWebButtons.animate().alpha(1f).setDuration(250).start()
        }
        uiHandler.removeCallbacks(hideUiRunnable)
        uiHandler.postDelayed(hideUiRunnable, immersiveTimeout)
    }

    private fun hideButtons() {
        layoutWebButtons.animate().alpha(0f).setDuration(500).start()
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        currentActiveUrl = url
        isCameraMode = url.contains("action=stream") || url.contains("camera.html")

        if (isCameraMode) {
            layoutOsd.visibility = if (isOsdEnabled) View.VISIBLE else View.GONE
            if (isOsdEnabled) {
                osdHandler.removeCallbacks(osdRunnable)
                osdHandler.post(osdRunnable)
            }
            webView.loadUrl(url)
        } else {
            layoutOsd.visibility = View.GONE
            osdHandler.removeCallbacks(osdRunnable)
            webView.loadUrl(url)
        }
    }

    private fun fetchMoonrakerData() {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return

        val baseQuery = "printer/objects/query?extruder&heater_bed&print_stats&display_status" +
                "&output_pin%20fan0&output_pin%20fan2&temperature_fan%20chamber_fan"

        val urlStr = "http://$hostIp:7125/$baseQuery$cachedChamberQueryString"

        Thread {
            var responseText = ""
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                if (conn.responseCode == 200) {
                    responseText = conn.inputStream.bufferedReader().use { it.readText() }
                }
            } catch (_: Exception) {}

            // EVENT: DRUCKER GEHT OFFLINE
            if (responseText.isEmpty()) {
                runOnUiThread {
                    findViewById<TextView>(R.id.tvOsdProgress)?.text = getString(R.string.osd_printer_offline)
                    findViewById<TextView>(R.id.tvOsdProgress)?.setTextColor(Color.parseColor("#E53935"))
                    findViewById<TextView>(R.id.tvOsdTime)?.text = ""

                    if (!hasTrigOffline) {
                        hasTrigOffline = true

                        SoundManager.playLiveNotification("sound_offline")
                        // GEFIXT: Der doppelte Parameter und der fehlerhafte XML-Styleable-Rest wurden entfernt
                        NotificationManager.showLivePopup(
                            context = this@WebViewActivity,
                            prefKey = "popup_offline",
                            titleResId = R.string.notify_title_offline,
                            messageResId = R.string.notify_msg_offline
                        )
                    }
                }
                return@Thread
            }

            hasTrigOffline = false

            try {
                val json = JSONObject(responseText)
                val status = json.optJSONObject("result")?.optJSONObject("status") ?: return@Thread

                val extruder = status.optJSONObject("extruder")
                val tempExtruder = extruder?.optDouble("temperature", 0.0) ?: 0.0
                val targetExtruder = extruder?.optDouble("target", 0.0) ?: 0.0

                val bed = status.optJSONObject("heater_bed")
                val tempBed = bed?.optDouble("temperature", 0.0) ?: 0.0
                val targetBed = bed?.optDouble("target", 0.0) ?: 0.0

                val displayStatus = status.optJSONObject("display_status")
                val progress = displayStatus?.optDouble("progress", 0.0) ?: 0.0

                val printStats = status.optJSONObject("print_stats")
                val currentState = printStats?.optString("state", "") ?: ""
                val duration = printStats?.optInt("print_duration", 0) ?: 0

                runOnUiThread {
                    findViewById<TextView>(R.id.tvOsdExtruder).text = getString(R.string.osd_extruder, tempExtruder, targetExtruder)
                    findViewById<TextView>(R.id.tvOsdBed).text = getString(R.string.osd_bed, tempBed, targetBed)
                    findViewById<TextView>(R.id.tvOsdProgress)?.setTextColor(Color.parseColor("#1976D2"))
                    findViewById<TextView>(R.id.tvOsdProgress).text = String.format(Locale.getDefault(), "%.1f%%", progress * 100)

                    val passedMin = duration / 60
                    val passedSec = duration % 60
                    findViewById<TextView>(R.id.tvOsdTime).text = String.format(Locale.getDefault(), "%02d:%02d", passedMin, passedSec)

                    if (currentState != "printing") {
                        hasTrigFirstLayer = false
                        hasTrig50 = false
                        hasTrig75 = false
                        hasTrig90 = false
                        hasTrig100 = false
                    }

                    if (currentState == "printing") {
                        // 1. FIRST LAYER EVENT
                        if (progress >= 0.01 && !hasTrigFirstLayer) {
                            hasTrigFirstLayer = true
                            SoundManager.playLiveNotification("sound_first_layer")
                            NotificationManager.showLivePopup(
                                context = this@WebViewActivity,
                                prefKey = "popup_first_layer",
                                titleResId = R.string.notify_title_first_layer,
                                messageResId = R.string.notify_msg_first_layer
                            )
                        }

                        // 2. MEILENSTEIN: 50%
                        if (progress >= 0.50 && !hasTrig50) {
                            hasTrig50 = true
                            SoundManager.playLiveNotification("sound_50")
                            NotificationManager.showLivePopup(
                                context = this@WebViewActivity,
                                prefKey = "popup_50",
                                titleResId = R.string.notify_title_50,
                                messageResId = R.string.notify_msg_50
                            )
                        }

                        // 3. MEILENSTEIN: 75%
                        if (progress >= 0.75 && !hasTrig75) {
                            hasTrig75 = true
                            SoundManager.playLiveNotification("sound_75")
                            NotificationManager.showLivePopup(
                                context = this@WebViewActivity,
                                prefKey = "popup_75",
                                titleResId = R.string.notify_title_75,
                                messageResId = R.string.notify_msg_75
                            )
                        }

                        // 4. MEILENSTEIN: 90%
                        if (progress >= 0.90 && !hasTrig90) {
                            hasTrig90 = true
                            SoundManager.playLiveNotification("sound_90")
                            NotificationManager.showLivePopup(
                                context = this@WebViewActivity,
                                prefKey = "popup_90",
                                titleResId = R.string.notify_title_90,
                                messageResId = R.string.notify_msg_90
                            )
                        }
                    }

                    // 5. EVENT: DRUCK ERFOLGREICH BEENDET (100%)
                    if (currentState == "complete" && !hasTrig100) {
                        hasTrig100 = true
                        SoundManager.playLiveNotification("sound_100")
                        NotificationManager.showLivePopup(
                            context = this@WebViewActivity,
                            prefKey = "popup_100",
                            titleResId = R.string.notify_title_100,
                            messageResId = R.string.notify_msg_100
                        )
                    }

                    // 6. EVENT: KRITISCHER KLIPPER-FEHLER
                    if (currentState == "error" && lastPrintState != "error") {
                        SoundManager.playLiveNotification("sound_error")
                        NotificationManager.showLivePopup(
                            context = this@WebViewActivity,
                            prefKey = "popup_error",
                            titleResId = R.string.notify_title_error,
                            messageResId = R.string.notify_msg_error
                        )
                    }

                    lastPrintState = currentState
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun sendEmergencyStop() { /* Unberührte POST-Notfall-Logik */ }
}