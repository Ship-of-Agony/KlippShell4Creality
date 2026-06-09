package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.graphics.drawable.RotateDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
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
    private var cachedChamberQueryString: String = "&temperature_sensor%20chamber_temp&heater_generic%20chamber_heater"
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

    private var heartbeatTick = 0

    private var fanModelRotationAngle = 0f
    private var fanAuxRotationAngle = 0f
    private var fanChamberRotationAngle = 0f

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

    private var isPipWideRatio = true

    private val PIP_REQUEST_CODE_MAXIMIZE = 101
    private val PIP_REQUEST_CODE_RESIZE = 102
    private val PIP_ACTION_MAXIMIZE = "com.shipofagony.klippshell4creality.PIP_ACTION_MAXIMIZE"
    private val PIP_ACTION_RESIZE = "com.shipofagony.klippshell4creality.PIP_ACTION_RESIZE"
    private var pipReceiver: BroadcastReceiver? = null

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
        initFullScreenBinding()
    }

    private fun initFullScreenBinding() {
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

        if (currentActiveUrl.isEmpty()) {
            currentActiveUrl = if (isCameraDefault) {
                "http://$printerIp:$printerPort/camera.html"
            } else {
                "http://$printerIp:$printerPort"
            }
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
                    getString(R.string.menu_pip_name),
                    getString(R.string.menu_light_control),
                    getString(R.string.menu_screensaver),
                    getString(R.string.menu_ratio_title),
                    getString(R.string.menu_change_camera_type),
                    getString(R.string.menu_emergency_stop)
                )
                val menuColors = arrayOf<String?>(null, null, null, null, null, null, "#E53935")

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
                            enterPipMode()
                        }
                        2 -> {
                            isLightOn = !isLightOn
                            sendLightCommand(isLightOn)
                        }
                        3 -> {
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
                        4 -> {
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
                        5 -> {
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
                        6 -> {
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
                showCenteredPillToast(getString(R.string.toast_loading_livestream))
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipActions() {
        val actions = ArrayList<RemoteAction>()

        val maxIntent = Intent(PIP_ACTION_MAXIMIZE).setPackage(packageName)
        val maxPendingIntent = PendingIntent.getBroadcast(
            this, PIP_REQUEST_CODE_MAXIMIZE, maxIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val maxIcon = Icon.createWithResource(this, android.R.drawable.ic_menu_revert)
        val maxAction = RemoteAction(maxIcon, getString(R.string.menu_pip_name), getString(R.string.menu_pip_name), maxPendingIntent)
        actions.add(maxAction)

        val resizeIntent = Intent(PIP_ACTION_RESIZE).setPackage(packageName)
        val resizePendingIntent = PendingIntent.getBroadcast(
            this, PIP_REQUEST_CODE_RESIZE, resizeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resizeIcon = Icon.createWithResource(this, android.R.drawable.ic_menu_crop)
        val resizeAction = RemoteAction(resizeIcon, getString(R.string.btn_pip_resize), getString(R.string.btn_pip_resize), resizePendingIntent)
        actions.add(resizeAction)

        val pipParams = PictureInPictureParams.Builder()
            .setActions(actions)
            .setAspectRatio(if (isPipWideRatio) Rational(16, 9) else Rational(1, 1))
            .build()

        setPictureInPictureParams(pipParams)
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(pipParams)
        } else {
            showCenteredPillToast("PiP wird von diesem Gerät nicht unterstützt")
        }
    }

    private fun togglePipWindowSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            isPipWideRatio = !isPipWideRatio
            updatePipActions()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)

        if (isInPictureInPictureMode) {
            uiHandler.removeCallbacks(hideUiRunnable)
            screensaverHandler.removeCallbacks(startScreensaverRunnable)

            webView?.let { viewRef ->
                (viewRef.parent as? ViewGroup)?.removeView(viewRef)
            }

            setContentView(R.layout.activity_pip_status)

            val videoContainer = findViewById<FrameLayout>(R.id.pipVideoContainer)
            webView?.let { viewRef ->
                videoContainer?.addView(viewRef, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
            }

            val tvPipProgress = findViewById<TextView>(R.id.tvPipProgress)
            if (lastProgressPercent >= 0.0) {
                val progressText = String.format(Locale.getDefault(), "%.1f%%", lastProgressPercent * 100)
                tvPipProgress?.text = progressText
            } else {
                tvPipProgress?.text = "--%"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                updatePipActions()
            }

            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) return
                    when (intent.action) {
                        PIP_ACTION_MAXIMIZE -> {
                            val maxIntent = Intent(this@WebViewActivity, WebViewActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                            startActivity(maxIntent)
                        }
                        PIP_ACTION_RESIZE -> {
                            togglePipWindowSize()
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(PIP_ACTION_MAXIMIZE)
                addAction(PIP_ACTION_RESIZE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, filter)
            }

            if (pollingJob == null || !pollingJob!!.isActive) {
                isOsdEnabled = true
                startOsdPolling()
            }

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)

        } else {
            try {
                pipReceiver?.let { unregisterReceiver(it) }
            } catch (_: Exception) {}
            pipReceiver = null

            webView?.let { viewRef ->
                (viewRef.parent as? ViewGroup)?.removeView(viewRef)
            }

            initFullScreenBinding()

            val staticWebViewPlaceholder = findViewById<WebView>(R.id.webView)
            val rootWebViewContainer = staticWebViewPlaceholder?.parent as? ViewGroup
            if (staticWebViewPlaceholder != null && webView != null) {
                val index = rootWebViewContainer?.indexOfChild(staticWebViewPlaceholder) ?: -1
                if (index != -1) {
                    val lp = staticWebViewPlaceholder.layoutParams
                    rootWebViewContainer?.removeViewAt(index)
                    rootWebViewContainer?.addView(webView, index, lp)
                }
            }

            if (isOsdEnabled && isCameraMode) {
                startOsdPolling()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isInPictureInPictureMode) {
            showButtons()
            resetInactivityTimer()
            btnToggle.post { btnToggle.requestFocus() }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (layoutScreensaver.visibility == View.VISIBLE) {
            deactivateScreensaver()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isInPictureInPictureMode) return super.onKeyDown(keyCode, event)

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
        if (isCameraMode && screensaverTimeoutMs > 0L && layoutScreensaver.visibility != View.VISIBLE && !isInPictureInPictureMode) {
            screensaverHandler.postDelayed(startScreensaverRunnable, screensaverTimeoutMs)
        }
    }

    private fun activateScreensaver() {
        if (isInPictureInPictureMode) return
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
            cachedMoonrakerUrl = URL("http://$hostIp:7125/printer/objects/query?extruder&heater_bed&print_stats&display_status" +
                    "&output_pin%20fan0&output_pin%20fan2&temperature_fan%20chamber_fan&output_pin%20LED$cachedChamberQueryString")
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

        if (!isInPictureInPictureMode) {
            val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
            val savedPosition = prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center"
            applyOsdPositionAndStyle(savedPosition)
        }

        pollingJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                fetchMoonrakerData()
                delay(3000)
            }
        }
    }

    private fun stopOsdPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun getProgressiveStepSpeed(fanSpeedPercent: Double): Float {
        if (fanSpeedPercent <= 0.0) return 0f
        return when {
            fanSpeedPercent <= 25.0 -> 3.5f
            fanSpeedPercent <= 50.0 -> 9.5f
            fanSpeedPercent <= 75.0 -> 22.0f
            else -> 48.0f
        }
    }

    private fun prepareRotatedFanIcon(context: Context, resId: Int, angle: Float, isNight: Boolean): Drawable? {
        val baseDrawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        val color = if (isNight) Color.WHITE else Color.BLACK
        baseDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        val size = (18 * context.resources.displayMetrics.density).toInt()
        baseDrawable.setBounds(0, 0, size, size)

        val rotateWrapper = RotateDrawable().apply {
            drawable = baseDrawable
            fromDegrees = angle
            toDegrees = angle
            pivotX = 0.5f
            pivotY = 0.5f
            level = 10000
        }
        rotateWrapper.setBounds(0, 0, size, size)
        return rotateWrapper
    }

    private fun prepareOsdIcon(context: Context, resId: Int, isNight: Boolean): Drawable? {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        val color = if (isNight) Color.WHITE else Color.BLACK
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        val size = (18 * context.resources.displayMetrics.density).toInt()
        drawable.setBounds(0, 0, size, size)
        return drawable
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

    private fun showButtons() {
        if (layoutScreensaver.visibility == View.VISIBLE || isInPictureInPictureMode) return
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
                setColor(Color.parseColor("#73FAFAFA"))
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
                75.0f -> { params.dimensionRatio = "H,4:3" }
                100.0f -> { params.dimensionRatio = "H,1:1" }
                else -> { params.dimensionRatio = "H,16:9" }
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
                if (conn.responseCode == HttpURLConnection.HTTP_OK) isSuccess = true
            } catch (_: Exception) {} finally { conn?.disconnect() }

            withContext(Dispatchers.Main) {
                if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                    if (isSuccess) showCenteredPillToast(getString(if (turnOn) R.string.menu_light_on else R.string.menu_light_off) + " ✓")
                    else showCenteredPillToast(getString(R.string.toast_light_error))
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
                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NO_CONTENT) isSuccess = true
            } catch (_: Exception) {} finally { conn?.disconnect() }

            withContext(Dispatchers.Main) {
                if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                    if (isSuccess) showCenteredPillToast(getString(R.string.toast_stop_success))
                    else showCenteredPillToast(getString(R.string.toast_stop_error) + responseCode)
                }
            }
        }
    }

    private suspend fun fetchMoonrakerData() {
        val targetUrl = cachedMoonrakerUrl ?: return
        var conn: HttpURLConnection? = null
        val responseText = try {
            conn = targetUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else ""
        } catch (_: Exception) { "" } finally { conn?.disconnect() }

        withContext(Dispatchers.Main) {
            if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                handleMoonrakerResponse(responseText)
            }
        }
    }

    private fun handleMoonrakerResponse(responseText: String) {
        if (responseText.isEmpty()) {
            if (!isInPictureInPictureMode) {
                if (tvOsdProgress?.text != getString(R.string.osd_printer_offline)) {
                    tvOsdProgress?.text = getString(R.string.osd_printer_offline)
                    tvOsdProgress?.setTextColor(Color.parseColor("#E53935"))
                    tvOsdTime?.text = ""
                }
            }
            return
        }

        hasTrigOffline = false
        heartbeatTick = if (heartbeatTick >= 5) 1 else heartbeatTick + 1

        try {
            val json = JSONObject(responseText)
            val status = json.optJSONObject("result")?.optJSONObject("status") ?: return

            val ledPinObj = status.optJSONObject("output_pin LED")
            if (ledPinObj != null) isLightOn = ledPinObj.optDouble("value", 0.0) > 0.0

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

            var fanModelSpeed = 0.0
            val fan0Obj = status.optJSONObject("output_pin fan0")
            if (fan0Obj != null) {
                fanModelSpeed = (if (fan0Obj.has("value")) fan0Obj.optDouble("value", 0.0) else fan0Obj.optDouble("speed", 0.0)) * 100.0
            }

            var fanAuxSpeed = 0.0
            val fan2Obj = status.optJSONObject("output_pin fan2")
            if (fan2Obj != null) {
                fanAuxSpeed = (if (fan2Obj.has("value")) fan2Obj.optDouble("value", 0.0) else fan2Obj.optDouble("speed", 0.0)) * 100.0
            }

            var fanChamberSpeed = 0.0
            val tempFanKey = "temperature_fan chamber_fan"
            val heatFanKey = "heater_fan chamber_fan"
            if (status.has(tempFanKey)) {
                val obj = status.optJSONObject(tempFanKey)
                if (obj != null) fanChamberSpeed = (if (obj.has("speed")) obj.optDouble("speed", 0.0) else obj.optDouble("value", 0.0)) * 100.0
            } else if (status.has(heatFanKey)) {
                val obj = status.optJSONObject(heatFanKey)
                if (obj != null) fanChamberSpeed = (if (obj.has("value")) obj.optDouble("value", 0.0) else obj.optDouble("speed", 0.0)) * 100.0
            }

            val displayStatus = status.optJSONObject("display_status")
            val progress = displayStatus?.optDouble("progress", 0.0) ?: 0.0

            val printStats = status.optJSONObject("print_stats")
            val currentState = printStats?.optString("state", "") ?: ""
            val duration = printStats?.optInt("print_duration", 0) ?: 0

            lastProgressPercent = progress

            if (!isInPictureInPictureMode) {
                val isHorizontal = (layoutOsd as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL
                val divider = if (isHorizontal) "  •  " else ""
                val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                val osdViews = arrayOf(
                    tvOsdExtruder, tvOsdBed, tvOsdChamberSensor, tvOsdChamberHeater,
                    tvOsdFanModel, tvOsdFanAux, tvOsdFanChamber, tvOsdProgress, tvOsdTime
                )
                osdViews.forEach { tv -> tv?.setTextColor(if (isNight) Color.WHITE else Color.BLACK) }

                tvOsdExtruder?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.fluid_24, isNight), null, null, null)
                tvOsdBed?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.windshield_defrost_rear_24, isNight), null, null, null)
                tvOsdChamberSensor?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.bottom_navigation_24, isNight), null, null, null)
                tvOsdChamberHeater?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.thermostat_24, isNight), null, null, null)

                if (fanModelSpeed > 0.0) { fanModelRotationAngle = (fanModelRotationAngle + getProgressiveStepSpeed(fanModelSpeed)) % 360f }
                if (fanAuxSpeed > 0.0) { fanAuxRotationAngle = (fanAuxRotationAngle - getProgressiveStepSpeed(fanAuxSpeed)) % 360f }
                if (fanChamberSpeed > 0.0) { fanChamberRotationAngle = (fanChamberRotationAngle + getProgressiveStepSpeed(fanChamberSpeed)) % 360f }

                tvOsdFanModel?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanModelRotationAngle, isNight), null, null, null)
                tvOsdFanAux?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanAuxRotationAngle, isNight), null, null, null)
                tvOsdFanChamber?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanChamberRotationAngle, isNight), null, null, null)

                tvOsdProgress?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.ic_progress_clock, isNight), null, null, null)
                tvOsdTime?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.hourglass_top_24, isNight), null, null, null)

                val padPx = (6 * resources.displayMetrics.density).toInt()
                osdViews.forEach { it?.compoundDrawablePadding = padPx }

                tvOsdExtruder?.text = " " + getString(R.string.osd_extruder, tempExtruder, targetExtruder) + divider
                tvOsdBed?.text = " " + getString(R.string.osd_bed, tempBed, targetBed) + divider

                tvOsdChamberSensor?.visibility = View.VISIBLE
                tvOsdChamberSensor?.text = " " + getString(R.string.osd_chamber_sensor, tempChamber) + divider

                tvOsdChamberHeater?.visibility = View.VISIBLE
                tvOsdChamberHeater?.text = " " + getString(R.string.osd_chamber_heater, tempChamberHeater, targetChamberHeater) + divider

                tvOsdFanModel?.text = " " + getString(R.string.osd_fan_model, fanModelSpeed) + divider
                tvOsdFanAux?.text = " " + getString(R.string.osd_fan_aux, fanAuxSpeed) + divider
                tvOsdFanChamber?.text = " " + getString(R.string.osd_fan_chamber, fanChamberSpeed) + divider

                val progressText = String.format(Locale.getDefault(), "%.1f%%", progress * 100)
                tvOsdProgress?.text = " " + getString(R.string.osd_progress_label, progressText) + divider

                val passedMin = duration / 60
                val passedSec = duration % 60
                val timeDigits = String.format(Locale.getDefault(), "%02d:%02d", passedMin, passedSec)
                tvOsdTime?.text = " " + getString(R.string.osd_time, timeDigits, "--:--") + " " + ".".repeat(heartbeatTick)

                layoutOsd.invalidate()
                layoutOsd.requestLayout()
            }
        } catch (_: Exception) {}
    }

    private fun runChamberAutoSearch(status: JSONObject) {
        for (sensor in chamberSensorsToTry) { if (status.has(sensor)) { knownChamberSensor = sensor; break } }
        for (heater in chamberHeatersToTry) { if (status.has(heater)) { knownChamberHeater = heater; break } }
        cachedChamberQueryString = "&${Uri.encode(knownChamberSensor)}&${Uri.encode(knownChamberHeater)}"
        val uri = Uri.parse(currentActiveUrl)
        updateMoonrakerUrl(uri.host ?: return)
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
            else -> {
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

    private fun showPillDialog(title: String, items: Array<String>, hexColors: Array<String?>? = null, onSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

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
                    backgroundTintList = ColorStateList.valueOf(if (isNight) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888"))
                    setTextColor(if (isNight) Color.WHITE else Color.BLACK)
                }
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }
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
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }
            container?.addView(btn)
        }
        dialog.show()
    }

    private fun showScreensaverPillDialog(title: String, items: Array<String>, onSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val activeTimeout = prefs.getLong("screensaver_timeout_global_fallback", 120 * 60 * 1000L)

        items.forEachIndexed { index, itemText ->
            val targetMs = when (index) { 1 -> 30 * 60 * 1000L; 2 -> 60 * 60 * 1000L; 3 -> 90 * 60 * 1000L; 4 -> 120 * 60 * 1000L; else -> -1L }
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
                    backgroundTintList = ColorStateList.valueOf(if (isNight) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888"))
                    setTextColor(if (isNight) Color.WHITE else Color.BLACK)
                }
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }
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
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }
            container?.addView(btn)
        }
        dialog.show()
    }
}