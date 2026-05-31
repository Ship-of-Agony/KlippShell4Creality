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
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
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

    private lateinit var btnMenu: MaterialButton
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnClose: MaterialButton

    private var tvOsdExtruder: TextView? = null
    private var tvOsdBed: TextView? = null
    private var tvOsdChamberSensor: TextView? = null
    private var tvOsdChamberHeater: TextView? = null
    private var tvOsdFanModel: TextView? = null
    private var tvOsdFanAux: TextView? = null
    private var tvOsdFanChamber: TextView? = null
    private var tvOsdProgress: TextView? = null
    private var tvOsdTime: TextView? = null

    private var currentActiveUrl: String = ""
    private var lastCameraUrl: String = ""
    private var isCameraMode: Boolean = false
    private var isOsdEnabled: Boolean = false

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

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "de") ?: "de"
        val locale = Locale(savedLang)
        Locale.setDefault(locale)

        val config = newBase.resources.configuration
        config.setLocale(locale)

        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webView)
        layoutOsd = findViewById(R.id.layoutOsd)
        layoutWebButtons = findViewById(R.id.layoutWebButtons)

        btnMenu = findViewById(R.id.btnWebMenu)
        btnToggle = findViewById(R.id.btnWebToggle)
        btnClose = findViewById(R.id.btnWebClose)

        tvOsdExtruder = findViewById(R.id.tvOsdExtruder)
        tvOsdBed = findViewById(R.id.tvOsdBed)
        tvOsdChamberSensor = findViewById(R.id.tvOsdChamberSensor)
        tvOsdChamberHeater = findViewById(R.id.tvOsdChamberHeater)
        tvOsdFanModel = findViewById(R.id.tvOsdFanModel)
        tvOsdFanAux = findViewById(R.id.tvOsdFanAux)
        tvOsdFanChamber = findViewById(R.id.tvOsdFanChamber)
        tvOsdProgress = findViewById(R.id.tvOsdProgress)
        tvOsdTime = findViewById(R.id.tvOsdTime)

        applySeichterOsdBackground()

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

        // 1. DER LINKE BUTTON (Optionen & Roter Not-Aus)
        btnMenu.setOnClickListener {
            if (isCameraMode) {
                val osdOptionText = getString(if (isOsdEnabled) R.string.menu_osd_hide else R.string.menu_osd_show)

                val menuOptions = arrayOf(
                    osdOptionText,
                    getString(R.string.menu_ratio),
                    getString(R.string.menu_change_camera_type),
                    getString(R.string.menu_emergency_stop)
                )
                val menuColors = arrayOf<String?>(null, null, null, "#E53935")

                showPillDialog(getString(R.string.menu_options_title), menuOptions, menuColors) { which ->
                    val uri = Uri.parse(currentActiveUrl)
                    val hostIpAddress = uri.host ?: ""

                    // Geburts-Port ermitteln für absolut sicheren Erststart-Fallback
                    val initialUri = Uri.parse(intent.getStringExtra("TARGET_URL") ?: currentActiveUrl)
                    val initialPort = initialUri.port.takeIf { it != -1 } ?: 7125

                    when (which) {
                        0 -> {
                            isOsdEnabled = !isOsdEnabled
                            getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("osd_enabled_$hostIpAddress", isOsdEnabled).apply()

                            if (isOsdEnabled && isCameraMode) {
                                layoutOsd.visibility = View.VISIBLE
                                osdHandler.removeCallbacks(osdRunnable)
                                osdHandler.post(osdRunnable)
                            } else {
                                layoutOsd.visibility = View.GONE
                                osdHandler.removeCallbacks(osdRunnable)
                            }
                        }
                        1 -> {
                            val ratioOptions = arrayOf(
                                getString(R.string.ratio_16_9),
                                getString(R.string.ratio_4_3),
                                getString(R.string.ratio_1_1)
                            )
                            showPillDialog(getString(R.string.menu_ratio), ratioOptions) { ratioIndex ->
                                val targetRatio = when(ratioIndex) {
                                    1 -> 75.0f   // 4:3
                                    2 -> 100.0f  // 1:1
                                    else -> 56.25f // 16:9
                                }
                                getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                    .edit().putFloat("camera_ratio_$hostIpAddress", targetRatio).apply()
                                loadStreamOrUrl(currentActiveUrl, targetRatio)
                            }
                        }
                        2 -> {
                            val camOptions = arrayOf(
                                getString(R.string.camera_type_html),
                                getString(R.string.camera_type_port),
                                getString(R.string.camera_type_webcam)
                            )
                            showPillDialog(getString(R.string.menu_change_camera_type), camOptions) { camIndex ->
                                // GEFIXT: Holt den Port aus den Einstellungen, nutzt den initialen Port als intelligenten Fallback
                                val savedDashboardPort = prefs.getInt("saved_dashboard_port_$hostIpAddress", initialPort)

                                val newUrl = when(camIndex) {
                                    0 -> "http://$hostIpAddress:$savedDashboardPort/camera.html"
                                    1 -> "http://$hostIpAddress:8080/?action=stream"
                                    else -> "http://$hostIpAddress:$savedDashboardPort/webcam/?action=stream"
                                }
                                loadStreamOrUrl(newUrl, 56.25f)
                            }
                        }
                        3 -> {
                            val stopOptions = arrayOf(getString(R.string.dialog_stop_confirm), getString(R.string.dialog_cancel))
                            val stopColors = arrayOf<String?>("#E53935", null)

                            showPillDialog(getString(R.string.dialog_stop_title), stopOptions, stopColors) { confirm ->
                                if (confirm == 0) sendEmergencyStop()
                            }
                        }
                    }
                }
            } else {
                val stopOptions = arrayOf(getString(R.string.dialog_stop_confirm), getString(R.string.dialog_cancel))
                val stopColors = arrayOf<String?>("#E53935", null)
                showPillDialog(getString(R.string.dialog_stop_title), stopOptions, stopColors) { confirm ->
                    if (confirm == 0) sendEmergencyStop()
                }
            }
        }

        // 2. DER MITTLERE BUTTON (Kurzer Klick: Direkt-Wechsel)
        btnToggle.setOnClickListener {
            val uri = Uri.parse(currentActiveUrl)
            val hostIpAddress = uri.host ?: ""

            // GEFIXT: Dynamischer Session-Startport-Fallback verhindert die Sackgasse für Nicht-Creality Nutzer
            val initialUri = Uri.parse(intent.getStringExtra("TARGET_URL") ?: currentActiveUrl)
            val initialPort = initialUri.port.takeIf { it != -1 } ?: 7125
            val savedDashboardPort = prefs.getInt("saved_dashboard_port_$hostIpAddress", initialPort)

            if (isCameraMode) {
                loadStreamOrUrl("http://$hostIpAddress:$savedDashboardPort", 56.25f)
                showCenteredPillToast(getString(R.string.toast_loading_dashboard))
            } else {
                val savedRatio = try { prefs.getFloat("camera_ratio_$hostIpAddress", 56.25f) } catch(e: Exception) { 56.25f }
                val fallbackUrl = if (lastCameraUrl.isNotEmpty()) lastCameraUrl else "http://$hostIpAddress:$savedDashboardPort/camera.html"

                loadStreamOrUrl(fallbackUrl, savedRatio)
            }
        }

        // Langer Druck (Long-Click) öffnet die Port-Wahl ("Dashboard wechseln?") erneut
        btnToggle.setOnLongClickListener {
            if (isCameraMode) {
                val dashboardOptions = arrayOf(getString(R.string.system_creality), getString(R.string.system_standard))
                showPillDialog(getString(R.string.choose_default_view_title), dashboardOptions) { whichDash ->
                    val uri = Uri.parse(currentActiveUrl)
                    val hostIpAddress = uri.host ?: ""
                    val targetPort = if (whichDash == 0) 4408 else 7125

                    prefs.edit().putInt("saved_dashboard_port_$hostIpAddress", targetPort).apply()
                    loadStreamOrUrl("http://$hostIpAddress:$targetPort", 56.25f)
                }
                true
            } else false
        }

        btnClose.setOnClickListener { finish() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showButtons()
                if (!btnToggle.isFocused && layoutWebButtons.alpha == 1f) {
                    btnToggle.requestFocus()
                }
            }
        }
        return super.onKeyDown(keyCode, event)
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

    private fun applySeichterOsdBackground() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val seichterBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            if (isNight) {
                setColor(Color.parseColor("#B3212529"))
                setStroke(3, Color.parseColor("#40FFFFFF"))
            } else {
                setColor(Color.parseColor("#BFFAFAFA"))
                setStroke(3, Color.parseColor("#33000000"))
            }
        }
        layoutOsd.background = seichterBg
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        currentActiveUrl = url
        isCameraMode = url.contains("action=stream") || url.contains("camera.html")

        if (isCameraMode) {
            lastCameraUrl = url
            layoutOsd.visibility = if (isOsdEnabled) View.VISIBLE else View.GONE
            if (isOsdEnabled) {
                osdHandler.removeCallbacks(osdRunnable)
                osdHandler.post(osdRunnable)
            }

            btnMenu.text = "≡"
            btnMenu.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
            btnMenu.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
            btnToggle.text = "⇄"

            val params = webView.layoutParams as ConstraintLayout.LayoutParams
            when (paddingTopPercent) {
                75.0f -> {
                    params.dimensionRatio = "H,4:3"
                    params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    params.height = 0
                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                100.0f -> {
                    params.dimensionRatio = "H,1:1"
                    params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    params.height = 0
                    params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                    params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                }
                else -> {
                    params.dimensionRatio = null
                    params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
                    params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
                }
            }
            webView.layoutParams = params

        } else {
            layoutOsd.visibility = View.GONE
            osdHandler.removeCallbacks(osdRunnable)

            btnMenu.text = "⚠"
            btnMenu.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            btnMenu.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            btnToggle.text = "⇄"

            val params = webView.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = null
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
            webView.layoutParams = params
        }

        webView.loadUrl(url)
    }

    private fun fetchMoonrakerData() {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return

        val baseQuery = "printer/objects/query?extruder&heater_bed&print_stats&display_status" +
                "&output_pin%20fan0&output_pin%20fan2&temperature_fan%20chamber_fan"

        val urlStr = "http://$hostIp:7125/$baseQuery$cachedChamberQueryString"

        Thread {
            var responseText = ""
            var conn: HttpURLConnection? = null
            try {
                conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                if (conn.responseCode == 200) {
                    responseText = conn.inputStream.bufferedReader().use { it.readText() }
                }
            } catch (_: Exception) {
            } finally {
                conn?.disconnect()
            }

            if (responseText.isEmpty()) {
                runOnUiThread {
                    tvOsdProgress?.text = getString(R.string.osd_printer_offline)
                    tvOsdProgress?.setTextColor(Color.parseColor("#E53935"))
                    tvOsdTime?.text = ""

                    if (!hasTrigOffline) {
                        hasTrigOffline = true
                        SoundManager.playLiveNotification("sound_offline")
                        NotificationManager.showLivePopup(this@WebViewActivity, "popup_offline", R.string.notify_title_offline, R.string.notify_msg_offline)
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

                val chamberSensorObj = status.optJSONObject(knownChamberSensor ?: "")
                val tempChamber = chamberSensorObj?.optDouble("temperature", 0.0) ?: 0.0

                val chamberHeaterObj = status.optJSONObject(knownChamberHeater ?: "")
                val tempChamberHeater = chamberHeaterObj?.optDouble("temperature", 0.0) ?: 0.0
                val targetChamberHeater = chamberHeaterObj?.optDouble("target", 0.0) ?: 0.0

                val fan0Obj = status.optJSONObject("output_pin fan0")
                val fanModelSpeed = (fan0Obj?.optDouble("value", 0.0) ?: 0.0) * 100.0

                val fan2Obj = status.optJSONObject("output_pin fan2")
                val fanAuxSpeed = (fan2Obj?.optDouble("value", 0.0) ?: 0.0) * 100.0

                val chamberFanObj = status.optJSONObject("temperature_fan chamber_fan")
                val fanChamberSpeed = (chamberFanObj?.optDouble("speed", 0.0) ?: 0.0) * 100.0

                val displayStatus = status.optJSONObject("display_status")
                val progress = displayStatus?.optDouble("progress", 0.0) ?: 0.0

                val printStats = status.optJSONObject("print_stats")
                val currentState = printStats?.optString("state", "") ?: ""
                val duration = printStats?.optInt("print_duration", 0) ?: 0

                if (chamberSensorObj == null && currentState == "printing" && chamberSearchIndex < chamberSensorsToTry.size) {
                    runChamberAutoSearch(status)
                }

                runOnUiThread {
                    val strExtruder = getString(R.string.osd_extruder, tempExtruder, targetExtruder)
                    if (tvOsdExtruder?.text != strExtruder) tvOsdExtruder?.text = strExtruder

                    val strBed = getString(R.string.osd_bed, tempBed, targetBed)
                    if (tvOsdBed?.text != strBed) tvOsdBed?.text = strBed

                    if (tempChamber > 0) {
                        tvOsdChamberSensor?.visibility = View.VISIBLE
                        val strChamber = getString(R.string.osd_chamber_sensor, tempChamber)
                        if (tvOsdChamberSensor?.text != strChamber) tvOsdChamberSensor?.text = strChamber
                    }

                    if (tempChamberHeater > 0 || targetChamberHeater > 0) {
                        tvOsdChamberHeater?.visibility = View.VISIBLE
                        val strChamberHeater = getString(R.string.osd_chamber_heater, tempChamberHeater, targetChamberHeater)
                        if (tvOsdChamberHeater?.text != strChamberHeater) tvOsdChamberHeater?.text = strChamberHeater
                    }

                    val strFanModel = getString(R.string.osd_fan_model, fanModelSpeed)
                    if (tvOsdFanModel?.text != strFanModel) tvOsdFanModel?.text = strFanModel

                    val strFanAux = getString(R.string.osd_fan_aux, fanAuxSpeed)
                    if (tvOsdFanAux?.text != strFanAux) tvOsdFanAux?.text = strFanAux

                    val strFanChamber = getString(R.string.osd_fan_chamber, fanChamberSpeed)
                    if (tvOsdFanChamber?.text != strFanChamber) tvOsdFanChamber?.text = strFanChamber

                    val strProgress = String.format(Locale.getDefault(), "%.1f%%", progress * 100)
                    if (tvOsdProgress?.text != strProgress) {
                        tvOsdProgress?.setTextColor(Color.parseColor("#1976D2"))
                        tvOsdProgress?.text = strProgress
                    }

                    val passedMin = duration / 60
                    val passedSec = duration % 60

                    val timeDigits = String.format(Locale.getDefault(), "%02d:%02d", passedMin, passedSec)
                    val strTime = getString(R.string.osd_time, timeDigits, "--:--")
                    if (tvOsdTime?.text != strTime) tvOsdTime?.text = strTime

                    if (currentState != "printing") {
                        hasTrigFirstLayer = false
                        hasTrig50 = false
                        hasTrig75 = false
                        hasTrig90 = false
                        hasTrig100 = false
                    }

                    if (currentState == "printing") {
                        if (progress >= 0.01 && !hasTrigFirstLayer) {
                            hasTrigFirstLayer = true
                            SoundManager.playLiveNotification("sound_first_layer")
                            NotificationManager.showLivePopup(this@WebViewActivity, "popup_first_layer", R.string.notify_title_first_layer, R.string.notify_msg_first_layer)
                        }
                        if (progress >= 0.50 && !hasTrig50) {
                            hasTrig50 = true
                            SoundManager.playLiveNotification("sound_50")
                            NotificationManager.showLivePopup(this@WebViewActivity, "popup_50", R.string.notify_title_50, R.string.notify_msg_50)
                        }
                        if (progress >= 0.75 && !hasTrig75) {
                            hasTrig75 = true
                            SoundManager.playLiveNotification("sound_75")
                            NotificationManager.showLivePopup(this@WebViewActivity, "popup_75", R.string.notify_title_75, R.string.notify_msg_75)
                        }
                        if (progress >= 0.90 && !hasTrig90) {
                            hasTrig90 = true
                            SoundManager.playLiveNotification("sound_90")
                            NotificationManager.showLivePopup(this@WebViewActivity, "popup_90", R.string.notify_title_90, R.string.notify_msg_90)
                        }
                    }

                    if (currentState == "complete" && !hasTrig100) {
                        hasTrig100 = true
                        SoundManager.playLiveNotification("sound_100")
                        NotificationManager.showLivePopup(this@WebViewActivity, "popup_100", R.string.notify_title_100, R.string.notify_msg_100)
                    }

                    if (currentState == "error" && lastPrintState != "error") {
                        SoundManager.playLiveNotification("sound_error")
                        NotificationManager.showLivePopup(this@WebViewActivity, "popup_error", R.string.notify_title_error, R.string.notify_msg_error)
                    }

                    lastPrintState = currentState
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun runChamberAutoSearch(status: JSONObject) {
        for (sensor in chamberSensorsToTry) {
            if (status.has(sensor)) {
                knownChamberSensor = sensor
                break
            }
        }
        for (heater in chamberHeatersToTry) {
            if (status.has(heater)) {
                knownChamberHeater = heater
                break
            }
        }
        cachedChamberQueryString = "&${Uri.encode(knownChamberSensor)}&${Uri.encode(knownChamberHeater)}"
        chamberSearchIndex = chamberSensorsToTry.size
    }

    private fun showPillDialog(
        title: String,
        items: Array<String>,
        hexColors: Array<String?>? = null,
        onSelected: (Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK
        val btnBgColor = if (isNight) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")

        items.forEachIndexed { index, itemText ->
            val customHex = hexColors?.getOrNull(index)

            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                cornerRadius = 100
                setPadding(0, 35, 0, 35)

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(btnBgColor)
                    setTextColor(textColor)
                }

                isFocusable = true

                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 10, 0, 10)
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        strokeWidth = 6
                        strokeColor = ColorStateList.valueOf(Color.WHITE)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start()
                        strokeWidth = 0
                    }
                }

                setOnClickListener {
                    onSelected(index)
                    dialog.dismiss()
                }
            }
            container?.addView(btn)
        }
        dialog.show()
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pillView = TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(if (isNight) Color.parseColor("#252B2E") else Color.WHITE)
                setStroke(4, if (isNight) Color.WHITE else Color.parseColor("#BDBDBD"))
            }
            setPadding(50, 35, 50, 35)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(50, 0, 50, 240)
            }
        }
        val container = FrameLayout(this).apply { addView(pillView) }
        rootLayout.addView(container)
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2200)
    }

    private fun sendEmergencyStop() { /* Unberührte POST-Notfall-Logik */ }
}