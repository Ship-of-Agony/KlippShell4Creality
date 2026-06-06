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
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@Suppress("DEPRECATION", "Lint", "ClickableViewAccessibility", "SetJavaScriptEnabled", "SetTextI18n", "LocalSuppress")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "SetTextI18n", "DefaultLocale", "NewApi")
class WebViewActivity : AppCompatActivity() {

    private var webView: WebView? = null
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

    private var isLightOn: Boolean = false

    private lateinit var layoutScreensaver: FrameLayout
    private lateinit var ivScreensaverLogo: ImageView
    private var screensaverJob: Job? = null
    private var screensaverTimeoutMs: Long = 0L
    private var isInstantScreensaverActive: Boolean = false

    private val screensaverHandler = Handler(Looper.getMainLooper())
    private val startScreensaverRunnable = Runnable { activateScreensaver() }

    private var knownChamberSensor: String? = "temperature_sensor chamber_temp"
    private var knownChamberHeater: String? = "heater_generic chamber_heater"
    private var cachedChamberQueryString: String = "&amp;temperature_sensor%20chamber_temp&amp;heater_generic%20chamber_heater"
    private var chamberSearchIndex = 0

    private var hasTrigFirstLayer = false
    private var hasTrig50 = false
    private var hasTrig75 = false
    private var hasTrig90 = false
    private var hasTrig100 = false
    private var hasTrigOffline = false

    private var lastPrintState: String = ""
    private var pollingJob: Job? = null

    private var cachedMoonrakerUrl: URL? = null
    private var lastExtruderTemp = -1.0
    private var lastExtruderTarget = -1.0
    private var lastBedTemp = -1.0
    private var lastBedTarget = -1.0
    private var lastChamberTemp = -1.0
    private var lastChamberHeaterTemp = -1.0
    private var lastChamberHeaterTarget = -1.0
    private var lastFanModelSpeed = -1.0
    private var lastFanAuxSpeed = -1.0
    private var lastFanChamberSpeed = -1.0
    private var lastProgressPercent = -1.0
    private var lastDurationSeconds = -1

    private val chamberSensorsToTry = listOf(
        "temperature_sensor chamber_temp", "temperature_sensor chamber",
        "temperature_sensor enclosure_temp", "temperature_sensor enclosure"
    )
    private val chamberHeatersToTry = listOf(
        "heater_generic chamber_heater", "heater_generic chamber",
        "heater_generic enclosure_heater", "enclosure_heater"
    )

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideUiRunnable = Runnable { hideButtons() }
    private val immersiveTimeout = 5000L

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "de") ?: "de"
        val locale = Locale.forLanguageTag(savedLang)
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

        layoutScreensaver = findViewById(R.id.layoutScreensaver)
        ivScreensaverLogo = findViewById(R.id.ivScreensaverLogo)

        layoutScreensaver.setOnClickListener {
            if (layoutScreensaver.visibility == View.VISIBLE) {
                deactivateScreensaver()
            }
        }

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

        webView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (layoutScreensaver.visibility == View.VISIBLE) {
                    deactivateScreensaver()
                } else {
                    resetInactivityTimer()
                    showButtons()
                }
            }
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

        webView?.settings?.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        val printerIp = intent.getStringExtra("PRINTER_IP") ?: "127.0.0.1"
        val printerPort = intent.getStringExtra("PRINTER_PORT") ?: "7125"
        val isCameraDefault = intent.getBooleanExtra("IS_CAMERA_VIEW", false)

        currentActiveUrl = if (isCameraDefault) {
            "http://$printerIp:$printerPort/camera.html"
        } else {
            "http://$printerIp:$printerPort"
        }

        val hostIp = Uri.parse(currentActiveUrl).host ?: printerIp
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedRatio = try { prefs.getFloat("camera_ratio_$hostIp", 56.25f) } catch(e: Exception) { 56.25f }

        isOsdEnabled = prefs.getBoolean("osd_enabled_$hostIp", false)
        loadStreamOrUrl(currentActiveUrl, savedRatio)
        showButtons()

        val defaultTimeout = 120 * 60 * 1000L
        val globalFallback = prefs.getLong("screensaver_timeout_global_fallback", defaultTimeout)
        val savedSaverTime = prefs.getLong("screensaver_timeout_$hostIp", globalFallback)

        if (savedSaverTime > 0L) {
            screensaverTimeoutMs = savedSaverTime
            resetInactivityTimer()
        }

        btnMenu.setOnClickListener {
            if (isCameraMode) {
                val osdOptionText = getString(if (isOsdEnabled) R.string.menu_osd_hide else R.string.menu_osd_show)

                val menuOptions = arrayOf(
                    osdOptionText,
                    getString(R.string.menu_change_camera_type).substringBefore(" Live-Stream") + " Light",
                    getString(R.string.menu_screensaver),
                    getString(R.string.menu_ratio_title),
                    getString(R.string.menu_change_camera_type),
                    getString(R.string.menu_emergency_stop)
                )
                val menuColors = arrayOf<String?>(null, null, null, null, null, "#E53935")

                showPillDialog(getString(R.string.menu_options_title), menuOptions, menuColors) { which ->
                    val uri = Uri.parse(currentActiveUrl)
                    val hostIpAddress = uri.host ?: printerIp
                    val initialPort = uri.port.takeIf { it != -1 } ?: 7125

                    when (which) {
                        0 -> {
                            isOsdEnabled = !isOsdEnabled
                            getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("osd_enabled_$hostIpAddress", isOsdEnabled).apply()

                            if (isOsdEnabled && isCameraMode) {
                                layoutOsd.visibility = View.VISIBLE
                                startOsdPolling()
                            } else {
                                layoutOsd.visibility = View.GONE
                                stopOsdPolling()
                            }
                        }
                        1 -> {
                            isLightOn = !isLightOn
                            sendLightCommand(isLightOn)
                        }
                        2 -> {
                            val timeOptions = arrayOf(
                                getString(R.string.screensaver_now),
                                getString(R.string.screensaver_30),
                                getString(R.string.screensaver_60),
                                getString(R.string.screensaver_90),
                                getString(R.string.screensaver_120),
                                getString(R.string.screensaver_off)
                            )
                            showScreensaverPillDialog(getString(R.string.screensaver_title), timeOptions) { timeIndex ->
                                when (timeIndex) {
                                    0 -> {
                                        isInstantScreensaverActive = true
                                        activateScreensaver()
                                        return@showScreensaverPillDialog
                                    }
                                    1 -> { screensaverTimeoutMs = 30 * 60 * 1000L }
                                    2 -> { screensaverTimeoutMs = 60 * 60 * 1000L }
                                    3 -> { screensaverTimeoutMs = 90 * 60 * 1000L }
                                    4 -> { screensaverTimeoutMs = 120 * 60 * 1000L }
                                    5 -> { screensaverTimeoutMs = 0L }
                                }
                                isInstantScreensaverActive = false
                                prefs.edit().putLong("screensaver_timeout_$hostIpAddress", screensaverTimeoutMs).apply()
                                prefs.edit().putLong("screensaver_timeout_global_fallback", screensaverTimeoutMs).apply()
                                resetInactivityTimer()
                            }
                        }
                        3 -> {
                            val ratioOptions = arrayOf(
                                getString(R.string.ratio_16_9_new),
                                getString(R.string.ratio_4_3_new),
                                getString(R.string.ratio_1_1_new)
                            )
                            showPillDialog(getString(R.string.menu_ratio_title), ratioOptions) { ratioIndex ->
                                val targetRatio = when(ratioIndex) {
                                    0 -> 56.25f
                                    1 -> 75.0f
                                    else -> 100.0f
                                }
                                getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                    .edit().putFloat("camera_ratio_$hostIpAddress", targetRatio).apply()
                                loadStreamOrUrl(currentActiveUrl, targetRatio)
                            }
                        }
                        4 -> {
                            val savedDashboardPort = prefs.getInt("saved_dashboard_port_$hostIpAddress", initialPort)
                            val camOptions = arrayOf(
                                getString(R.string.camera_type_html_new),
                                getString(R.string.camera_type_port_new),
                                getString(R.string.camera_type_webcam_new)
                            )
                            showPillDialog(getString(R.string.menu_change_camera_type), camOptions) { camIndex ->
                                val newUrl = when(camIndex) {
                                    0 -> "http://$hostIpAddress:$savedDashboardPort/camera.html"
                                    1 -> "http://$hostIpAddress:8080/?action=stream"
                                    else -> "http://$hostIpAddress:$savedDashboardPort/webcam/?action=stream"
                                }
                                loadStreamOrUrl(newUrl, 56.25f)
                            }
                        }
                        5 -> {
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

        btnMenu.setOnLongClickListener {
            if (isCameraMode && isOsdEnabled) {
                val positionOptions = arrayOf(
                    getString(R.string.osd_pos_top_left),
                    getString(R.string.osd_pos_top_center),
                    getString(R.string.osd_pos_top_right),
                    getString(R.string.osd_pos_bottom_center)
                )
                showPillDialog(getString(R.string.osd_position_title), positionOptions) { index ->
                    val uri = Uri.parse(currentActiveUrl)
                    val hostIpAddress = uri.host ?: printerIp

                    val posStr = when(index) {
                        0 -> "top_left"
                        1 -> "top_center"
                        2 -> "top_right"
                        else -> "bottom_center"
                    }
                    getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                        .edit().putString("osd_position_$hostIpAddress", posStr).apply()

                    applyOsdPositionAndStyle(posStr)
                    showCenteredPillToast(getString(R.string.toast_position_saved))
                }
                true
            } else {
                showCenteredPillToast(getString(R.string.toast_position_fullscreen_only))
                true
            }
        }

        btnToggle.setOnClickListener {
            val uri = Uri.parse(currentActiveUrl)
            val hostIpAddress = uri.host ?: printerIp
            val initialPort = uri.port.takeIf { it != -1 } ?: 7125
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

        btnToggle.setOnLongClickListener {
            if (isCameraMode) {
                val dashboardOptions = arrayOf(getString(R.string.system_creality), getString(R.string.system_standard))
                showPillDialog(getString(R.string.choose_default_view_title), dashboardOptions) { whichDash ->
                    val uri = Uri.parse(currentActiveUrl)
                    val hostIpAddress = uri.host ?: printerIp
                    val targetPort = if (whichDash == 0) 4408 else 7125

                    prefs.edit().putInt("saved_dashboard_port_$hostIpAddress", targetPort).apply()
                    loadStreamOrUrl("http://$hostIpAddress:$targetPort", 56.25f)
                }
                true
            } else false
        }

        btnClose.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        showButtons()
        resetInactivityTimer()
        btnToggle.post { btnToggle.requestFocus() }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (layoutScreensaver.visibility == View.VISIBLE) {
            deactivateScreensaver()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (layoutScreensaver.visibility == View.VISIBLE) {
            deactivateScreensaver()
            return true
        }
        resetInactivityTimer()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                showButtons()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun resetInactivityTimer() {
        screensaverHandler.removeCallbacks(startScreensaverRunnable)
        if (isCameraMode && screensaverTimeoutMs > 0L && layoutScreensaver.visibility != View.VISIBLE) {
            screensaverHandler.postDelayed(startScreensaverRunnable, screensaverTimeoutMs)
        }
    }

    private fun activateScreensaver() {
        screensaverHandler.removeCallbacks(startScreensaverRunnable)

        uiHandler.removeCallbacks(hideUiRunnable)
        layoutWebButtons.visibility = View.GONE
        layoutOsd.visibility = View.GONE

        layoutScreensaver.visibility = View.VISIBLE
        layoutScreensaver.bringToFront()
        layoutScreensaver.requestFocus()

        screensaverJob?.cancel()
        screensaverJob = lifecycleScope.launch(Dispatchers.Main) {
            var posX = 150f
            var posY = 150f
            var speedX = 0.4f
            var speedY = 0.3f

            while (isActive) {
                val screenWidth = layoutScreensaver.width
                val screenHeight = layoutScreensaver.height
                val logoWidth = ivScreensaverLogo.width
                val logoHeight = ivScreensaverLogo.height

                if (screenWidth > 0 && screenHeight > 0 && logoWidth > 0 && logoHeight > 0) {
                    posX += speedX
                    posY += speedY

                    if (posX <= 0 || posX + logoWidth >= screenWidth) {
                        speedX *= -1
                        posX = posX.coerceIn(0f, (screenWidth - logoWidth).toFloat())
                    }
                    if (posY <= 0 || posY + logoHeight >= screenHeight) {
                        speedY *= -1
                        posY = posY.coerceIn(0f, (screenHeight - logoHeight).toFloat())
                    }

                    ivScreensaverLogo.x = posX
                    ivScreensaverLogo.y = posY
                }
                delay(32)
            }
        }
    }

    private fun deactivateScreensaver() {
        screensaverJob?.cancel()
        screensaverJob = null
        layoutScreensaver.visibility = View.GONE

        if (isInstantScreensaverActive) {
            isInstantScreensaverActive = false
            val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
            val defaultTimeout = 120 * 60 * 1000L
            val globalFallback = prefs.getLong("screensaver_timeout_global_fallback", defaultTimeout)
            screensaverTimeoutMs = globalFallback
        }

        resetInactivityTimer()

        if (isOsdEnabled) layoutOsd.visibility = View.VISIBLE
        showButtons()
        btnToggle.requestFocus()
    }

    private fun updateMoonrakerUrl(hostIp: String) {
        try {
            val baseQuery = "printer/objects/query?extruder&amp;heater_bed&amp;print_stats&amp;display_status" +
                    "&amp;output_pin%20fan0&amp;output_pin%20fan2&amp;temperature_fan%20chamber_fan&amp;output_pin%20LED"
            cachedMoonrakerUrl = URL("http://$hostIp:7125/$baseQuery$cachedChamberQueryString")
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Erzeugen der Polling URL", e)
            cachedMoonrakerUrl = null
        }
    }

    private fun startOsdPolling() {
        pollingJob?.cancel()
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        updateMoonrakerUrl(hostIp)

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedPosition = prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center"
        applyOsdPositionAndStyle(savedPosition)

        pollingJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && isOsdEnabled && isCameraMode) {
                fetchMoonrakerData()
                delay(3000)
            }
        }
    }

    private fun stopOsdPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    override fun onPause() {
        // Sicherstellen, dass das injizierte Overlay beim Activity-Wechsel sauber entfernt wird
        NotificationManager.dismissActivePopup()
        super.onPause()
    }

    override fun onStop() {
        stopOsdPolling()
        NotificationManager.dismissActivePopup()
        screensaverHandler.removeCallbacks(startScreensaverRunnable)
        screensaverJob?.cancel()
        uiHandler.removeCallbacks(hideUiRunnable)
        SoundManager.stopAllSounds()
        super.onStop()
    }

    override fun onDestroy() {
        stopOsdPolling()
        NotificationManager.dismissActivePopup()
        screensaverHandler.removeCallbacks(startScreensaverRunnable)
        screensaverJob?.cancel()
        uiHandler.removeCallbacks(hideUiRunnable)
        currentFocus?.clearFocus()

        try {
            webView?.let { view ->
                val parent = view.parent as? ViewGroup
                parent?.removeView(view)
                view.stopLoading()
                view.settings.javaScriptEnabled = false
                view.clearHistory()
                view.clearCache(true)
                view.loadUrl("about:blank")
                view.onPause()
                view.removeAllViews()
                view.destroy()
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Zerstören der WebView", e)
        }
        webView = null
        super.onDestroy()
    }

    private fun showButtons() {
        if (layoutScreensaver.visibility == View.VISIBLE) return

        uiHandler.removeCallbacks(hideUiRunnable)
        if (layoutWebButtons.visibility != View.VISIBLE) {
            layoutWebButtons.visibility = View.VISIBLE
        }
        layoutWebButtons.animate().alpha(1f).setDuration(250).start()
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

    private fun resetPrintTriggers() {
        hasTrigFirstLayer = false
        hasTrig50 = false
        hasTrig75 = false
        hasTrig90 = false
        hasTrig100 = false
        hasTrigOffline = false
        lastPrintState = ""

        lastExtruderTemp = -1.0
        lastExtruderTarget = -1.0
        lastBedTemp = -1.0
        lastBedTarget = -1.0
        lastChamberTemp = -1.0
        lastChamberHeaterTemp = -1.0
        lastChamberHeaterTarget = -1.0
        lastFanModelSpeed = -1.0
        lastFanAuxSpeed = -1.0
        lastFanChamberSpeed = -1.0
        lastProgressPercent = -1.0
        lastDurationSeconds = -1
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        resetPrintTriggers()
        currentActiveUrl = url
        isCameraMode = url.contains("action=stream") || url.contains("camera.html")

        if (isCameraMode) {
            lastCameraUrl = url
            layoutOsd.visibility = if (isOsdEnabled) View.VISIBLE else View.GONE
            if (isOsdEnabled) {
                startOsdPolling()
            }

            btnMenu.text = "≡"
            btnMenu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            btnMenu.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
            btnToggle.text = "⇄"

            val viewRef = webView ?: return
            val params = viewRef.layoutParams as ConstraintLayout.LayoutParams
            when (paddingTopPercent) {
                75.0f -> {
                    params.dimensionRatio = "H,4:3"
                }
                100.0f -> {
                    params.dimensionRatio = "H,1:1"
                }
                else -> {
                    params.dimensionRatio = "H,16:9"
                }
            }
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.height = 0
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            viewRef.layoutParams = params

        } else {
            layoutOsd.visibility = View.GONE
            stopOsdPolling()

            btnMenu.text = "⚠"
            btnMenu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            btnMenu.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            btnToggle.text = "⇄"

            val viewRef = webView ?: return
            val params = viewRef.layoutParams as ConstraintLayout.LayoutParams
            params.dimensionRatio = null
            params.width = ConstraintLayout.LayoutParams.MATCH_PARENT
            params.height = ConstraintLayout.LayoutParams.MATCH_PARENT
            viewRef.layoutParams = params
        }

        webView?.loadUrl(url)
    }

    private suspend fun fetchMoonrakerData() {
        val targetUrl = cachedMoonrakerUrl ?: return

        var conn: HttpURLConnection? = null
        val responseText = try {
            conn = targetUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
        } catch (_: Exception) {
            ""
        } finally {
            conn?.disconnect()
        }

        withContext(Dispatchers.Main) {
            if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                handleMoonrakerResponse(responseText)
            }
        }
    }

    private fun handleMoonrakerResponse(responseText: String) {
        if (responseText.isEmpty()) {
            if (tvOsdProgress?.text != getString(R.string.osd_printer_offline)) {
                tvOsdProgress?.text = getString(R.string.osd_printer_offline)
                tvOsdProgress?.setTextColor(Color.parseColor("#E53935"))
                tvOsdTime?.text = ""
            }

            if (!hasTrigOffline) {
                hasTrigOffline = true
                SoundManager.playLiveNotification("sound_offline")
                NotificationManager.showLivePopup(this, "popup_offline", R.string.notify_title_offline, R.string.notify_msg_offline)
            }
            return
        }

        hasTrigOffline = false

        try {
            val json = JSONObject(responseText)
            val status = json.optJSONObject("result")?.optJSONObject("status") ?: return

            val ledPinObj = status.optJSONObject("output_pin LED")
            if (ledPinObj != null) {
                val ledValue = ledPinObj.optDouble("value", 0.0)
                isLightOn = ledValue > 0.0
            }

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

            val isHorizontal = (layoutOsd as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL
            val divider = if (isHorizontal) "  •  " else ""

            if (tempExtruder != lastExtruderTemp || targetExtruder != lastExtruderTarget) {
                lastExtruderTemp = tempExtruder
                lastExtruderTarget = targetExtruder
                tvOsdExtruder?.text = getString(R.string.osd_extruder, tempExtruder, targetExtruder) + divider
            }

            if (tempBed != lastBedTemp || targetBed != lastBedTarget) {
                lastBedTemp = tempBed
                lastBedTarget = targetBed
                tvOsdBed?.text = getString(R.string.osd_bed, tempBed, targetBed) + divider
            }

            if (tempChamber > 0 && tempChamber != lastChamberTemp) {
                lastChamberTemp = tempChamber
                tvOsdChamberSensor?.visibility = View.VISIBLE
                tvOsdChamberSensor?.text = getString(R.string.osd_chamber_sensor, tempChamber) + divider
            } else if (tempChamber <= 0) {
                tvOsdChamberSensor?.visibility = View.GONE
            }

            if ((tempChamberHeater > 0 || targetChamberHeater > 0) && (tempChamberHeater != lastChamberHeaterTemp || targetChamberHeater != lastChamberHeaterTarget)) {
                lastChamberHeaterTemp = tempChamberHeater
                lastChamberHeaterTarget = targetChamberHeater
                tvOsdChamberHeater?.visibility = View.VISIBLE
                tvOsdChamberHeater?.text = getString(R.string.osd_chamber_heater, tempChamberHeater, targetChamberHeater) + divider
            } else if (tempChamberHeater <= 0 && targetChamberHeater <= 0) {
                tvOsdChamberHeater?.visibility = View.GONE
            }

            if (fanModelSpeed != lastFanModelSpeed) {
                lastFanModelSpeed = fanModelSpeed
                tvOsdFanModel?.text = getString(R.string.osd_fan_model, fanModelSpeed) + divider
            }

            if (fanAuxSpeed != lastFanAuxSpeed) {
                lastFanAuxSpeed = fanAuxSpeed
                tvOsdFanAux?.text = getString(R.string.osd_fan_aux, fanAuxSpeed) + divider
            }

            if (fanChamberSpeed != lastFanChamberSpeed) {
                lastFanChamberSpeed = fanChamberSpeed
                tvOsdFanChamber?.text = getString(R.string.osd_fan_chamber, fanChamberSpeed) + divider
            }

            if (progress != lastProgressPercent) {
                lastProgressPercent = progress
                tvOsdProgress?.setTextColor(Color.parseColor("#1976D2"))
                val progressText = String.format(Locale.getDefault(), "%.1f%%", progress * 100)
                tvOsdProgress?.text = "Fortschritt: $progressText" + divider
            }

            if (duration != lastDurationSeconds) {
                lastDurationSeconds = duration
                val passedMin = duration / 60
                val passedSec = duration % 60
                val timeDigits = String.format(Locale.getDefault(), "%02d:%02d", passedMin, passedSec)
                tvOsdTime?.text = getString(R.string.osd_time, timeDigits, "--:--")
            }

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
                    NotificationManager.showLivePopup(this, "popup_first_layer", R.string.notify_title_first_layer, R.string.notify_msg_first_layer)
                }
                if (progress >= 0.50 && !hasTrig50) {
                    hasTrig50 = true
                    SoundManager.playLiveNotification("sound_50")
                    NotificationManager.showLivePopup(this, "popup_50", R.string.notify_title_50, R.string.notify_msg_50)
                }
                if (progress >= 0.75 && !hasTrig75) {
                    hasTrig75 = true
                    SoundManager.playLiveNotification("sound_75")
                    NotificationManager.showLivePopup(this, "popup_75", R.string.notify_title_75, R.string.notify_msg_75)
                }
                if (progress >= 0.90 && !hasTrig90) {
                    hasTrig90 = true
                    SoundManager.playLiveNotification("sound_90")
                    NotificationManager.showLivePopup(this, "popup_90", R.string.notify_title_90, R.string.notify_msg_90)
                }
            }

            if (currentState == "complete" && !hasTrig100) {
                hasTrig100 = true
                SoundManager.playLiveNotification("sound_100")
                NotificationManager.showLivePopup(this, "popup_100", R.string.notify_title_100, R.string.notify_msg_100)
            }

            if (currentState == "error" && lastPrintState != "error") {
                SoundManager.playLiveNotification("sound_error")
                NotificationManager.showLivePopup(this, "popup_error", R.string.notify_title_error, R.string.notify_msg_error)
            }

            lastPrintState = currentState

        } catch (_: Exception) {}
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
        cachedChamberQueryString = "&amp;${Uri.encode(knownChamberSensor)}&amp;${Uri.encode(knownChamberHeater)}"

        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        updateMoonrakerUrl(hostIp)

        chamberSearchIndex = chamberSensorsToTry.size
    }

    private fun applyOsdPositionAndStyle(position: String) {
        val root = findViewById<ConstraintLayout>(R.id.rootLayout) ?: return
        val container = layoutOsd as? LinearLayout ?: return

        val constraintSet = ConstraintSet()
        constraintSet.clone(root)

        constraintSet.clear(R.id.layoutOsd, ConstraintSet.TOP)
        constraintSet.clear(R.id.layoutOsd, ConstraintSet.BOTTOM)
        constraintSet.clear(R.id.layoutOsd, ConstraintSet.START)
        constraintSet.clear(R.id.layoutOsd, ConstraintSet.END)

        val marginHorizontal = 40
        val marginTopBottom = 30

        when (position) {
            "top_left" -> {
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom)
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginHorizontal)
                container.orientation = LinearLayout.VERTICAL
            }
            "top_center" -> {
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom)
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                container.orientation = LinearLayout.HORIZONTAL
            }
            "top_right" -> {
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom)
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, marginHorizontal)
                container.orientation = LinearLayout.VERTICAL
            }
            else -> { // bottom_center
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140)
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(R.id.layoutOsd, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                container.orientation = LinearLayout.HORIZONTAL
            }
        }
        constraintSet.applyTo(root)
        updateOsdTextForm(container.orientation == LinearLayout.HORIZONTAL)
    }

    private fun updateOsdTextForm(isSingleLine: Boolean) {
        val views = arrayOf(
            tvOsdExtruder, tvOsdBed, tvOsdChamberSensor, tvOsdChamberHeater,
            tvOsdFanModel, tvOsdFanAux, tvOsdFanChamber, tvOsdProgress, tvOsdTime
        )
        views.forEach { tv ->
            tv?.let {
                val lp = it.layoutParams as LinearLayout.LayoutParams
                if (isSingleLine) {
                    lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                    lp.setMargins(16, 4, 16, 4)
                } else {
                    lp.width = LinearLayout.LayoutParams.MATCH_PARENT
                    lp.setMargins(8, 2, 8, 2)
                }
                it.layoutParams = lp
            }
        }
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

    private fun showScreensaverPillDialog(
        title: String,
        items: Array<String>,
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

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val activeTimeout = prefs.getLong("screensaver_timeout_global_fallback", 120 * 60 * 1000L)

        items.forEachIndexed { index, itemText ->
            val targetMs = when (index) {
                1 -> 30 * 60 * 1000L
                2 -> 60 * 60 * 1000L
                3 -> 90 * 60 * 1000L
                4 -> 120 * 60 * 1000L
                else -> -1L
            }

            val isCurrentlyActive = (targetMs == activeTimeout) || (index == 5 && activeTimeout == 0L)

            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                cornerRadius = 100
                setPadding(0, 35, 0, 35)

                if (isCurrentlyActive) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
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

    private fun sendLightCommand(turnOn: Boolean) {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        val urlStr = "http://$hostIp:7125/printer/gcode/script"

        val value = if (turnOn) "1" else "0"
        val gcodeScript = "SET_PIN PIN=LED VALUE=$value"

        lifecycleScope.launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            var isSuccess = false
            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val postData = "script=" + Uri.encode(gcodeScript)
                conn.outputStream.use { os ->
                    os.write(postData.toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    isSuccess = true
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "Licht-GCode konnte nicht gesendet werden", e)
            } finally {
                conn?.disconnect()
            }

            withContext(Dispatchers.Main) {
                if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                    if (isSuccess) {
                        val statusMsg = getString(if (turnOn) R.string.menu_light_on else R.string.menu_light_off) + " ✓"
                        showCenteredPillToast(statusMsg)
                    } else {
                        showCenteredPillToast(getString(R.string.toast_light_error))
                    }
                }
            }
        }
    }

    private fun sendEmergencyStop() {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        val urlStr = "http://$hostIp:7125/printer/emergency_stop"

        lifecycleScope.launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            var isSuccess = false
            var responseCode = -1

            try {
                val url = URL(urlStr)
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.setRequestProperty("Content-Length", "0")

                responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                    isSuccess = true
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "Mainsail/Fluidd NOT-AUS fehlgeschlagen", e)
            } finally {
                conn?.disconnect()
            }

            withContext(Dispatchers.Main) {
                if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                    if (isSuccess) {
                        showCenteredPillToast(getString(R.string.toast_stop_success))
                    } else {
                        showCenteredPillToast(getString(R.string.toast_stop_error) + responseCode)
                    }
                }
            }
        }
    }
}