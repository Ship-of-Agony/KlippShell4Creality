package com.shipofagony.klippshell4creality

import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
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
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    private fun toPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private var webView: WebView? = null
    private var webView3D: WebView? = null
    private lateinit var layoutOsd: View
    private lateinit var layoutOsdBanner: View
    private lateinit var layoutWebButtons: LinearLayout
    private var containerWebNotification: View? = null

    private lateinit var btnMenu: MaterialButton
    private lateinit var btnToggle: MaterialButton
    private lateinit var btnClose: MaterialButton

    private lateinit var layoutScrollRight: LinearLayout
    private lateinit var btnScrollRightUp: MaterialButton
    private lateinit var btnScrollRightDown: MaterialButton

    private var tvOsdExtruder: TextView? = null
    private var tvOsdBed: TextView? = null
    private var tvOsdChamberSensor: TextView? = null
    private var tvOsdChamberHeater: TextView? = null
    private var tvOsdFanModel: TextView? = null
    private var tvOsdFanAux: TextView? = null
    private var tvOsdFanChamber: TextView? = null
    private var tvOsdProgress: TextView? = null
    private var tvOsdTime: TextView? = null

    private var tvOsdExtruderBanner: TextView? = null
    private var tvOsdBedBanner: TextView? = null
    private var tvOsdChamberSensorBanner: TextView? = null
    private var tvOsdChamberHeaterBanner: TextView? = null
    private var tvOsdFanModelBanner: TextView? = null
    private var tvOsdFanAuxBanner: TextView? = null
    private var tvOsdFanChamberBanner: TextView? = null
    private var tvOsdProgressBanner: TextView? = null
    private var tvOsdTimeBanner: TextView? = null

    private var currentActiveUrl: String = ""
    private var lastCameraUrl: String = ""
    private var isCameraMode: Boolean = false
    private var isOsdEnabled: Boolean = false
    private var currentAspectRatioPercent: Float = 56.25f
    private var isLightOn: Boolean = false

    private lateinit var layoutScreensaver: FrameLayout
    private lateinit var ivScreensaverLogo: ImageView

    private var hasTrigFirstLayer = false
    private var hasTrig50 = false
    private var hasTrig75 = false
    private var hasTrig90 = false
    private var hasTrig100 = false
    private var hasTrigOffline = false

    private var lastPrintState: String = ""
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
    private var heartbeatTick = 0

    private var fanModelRotationAngle = 0f
    private var fanAuxRotationAngle = 0f
    private var fanChamberRotationAngle = 0f

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideUiRunnable = Runnable { hideButtons() }
    private val immersiveTimeout = 5000L
    private var isPipWideRatio = true

    private val PIP_REQUEST_CODE_MAXIMIZE = 101
    private val PIP_REQUEST_CODE_RESIZE = 102
    private val PIP_ACTION_MAXIMIZE = "com.shipofagony.klippshell4creality.PIP_ACTION_MAXIMIZE"
    private val PIP_ACTION_RESIZE = "com.shipofagony.klippshell4creality.PIP_ACTION_RESIZE"
    private var pipReceiver: BroadcastReceiver? = null

    private lateinit var prefs: SharedPreferences
    private var isActivityInForeground = false

    private val channelErrorsId = "klippshell_errors_channel"
    private val channelInfoId = "klippshell_info_channel"

    private var isThumbnailEnabled = true
    private var isBenchyShowing = false
    private var currentGCodeFilename = ""
    private var thumbnailBitmap: android.graphics.Bitmap? = null
    private val thumbContainerId = View.generateViewId()
    private var ivThumbBackground: ImageView? = null
    private var ivThumbForeground: ImageView? = null
    private var clipDrawable: ClipDrawable? = null

    private lateinit var companionServerManager: CompanionServerManager
    private lateinit var thumbnailRenderHelper: ThumbnailRenderHelper
    private lateinit var screensaverManager: ScreensaverManager
    private lateinit var moonrakerPollManager: MoonrakerPollManager
    private lateinit var webViewMenuHelper: WebViewMenuHelper

    private val hostIp: String
        get() = Uri.parse(currentActiveUrl).host ?: ""

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "system") ?: "system"
        val config = Configuration(newBase.resources.configuration)

        if (savedLang != "system") {
            val locale = Locale(savedLang)
            Locale.setDefault(locale)
            config.setLocale(locale)
        }

        val savedTheme = prefs.getInt("app_theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedTheme) {
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES -> {
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO -> {
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
        }

        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    private fun getSafeString(key: String, fallback: String): String {
        return try {
            val id = resources.getIdentifier(key, "string", packageName)
            if (id != 0) getString(id) else fallback
        } catch (_: Exception) { fallback }
    }

    private fun triggerPopup(key: String, titleDef: String, msgDef: String) {
        try {
            val tId = resources.getIdentifier("notify_title_$key", "string", packageName).takeIf { it != 0 } ?: resources.getIdentifier("osd_position_title", "string", packageName)
            val mId = resources.getIdentifier("notify_msg_$key", "string", packageName).takeIf { it != 0 } ?: tId

            val finalizedTitle = getString(tId)
            val finalizedMessage = getString(mId)

            NotificationManager.showLivePopup(this, "popup_$key", tId, mId, thumbnailBitmap)

            val targetChannelId = if (key == "offline" || key == "error") channelErrorsId else channelInfoId
            sendNativeSystemNotification(finalizedTitle, finalizedMessage, targetChannelId, thumbnailBitmap)
        } catch (e: Exception) {
            Log.e("KlippShell", "Error triggering alerts", e)
        }
        SoundManager.playLiveNotification("sound_$key")
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
            val errorChannelName = getSafeString("channel_error_name", "Fehler & Statusmeldungen")
            val infoChannelName = getSafeString("channel_info_name", "Informationen & Meilensteine")

            val errorChannel = NotificationChannel(channelErrorsId, errorChannelName, AndroidNotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical printer errors and connection loss alerts"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }

            val infoChannel = NotificationChannel(channelInfoId, infoChannelName, AndroidNotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Printer progress updates and milestone completion alerts"
                enableLights(true)
                lightColor = Color.GREEN
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(errorChannel)
            notificationManager.createNotificationChannel(infoChannel)
        }
    }

    private fun sendNativeSystemNotification(title: String, message: String, targetChannelId: String, bitmap: android.graphics.Bitmap? = null) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        registerNotificationChannels()

        val intent = Intent(this, WebViewActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, targetChannelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (targetChannelId == channelErrorsId) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (targetChannelId == channelErrorsId) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (bitmap != null && targetChannelId == channelInfoId) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(NotificationCompat.BigPictureStyle()
                .bigPicture(bitmap)
                .bigLargeIcon(null as android.graphics.Bitmap?))
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        registerNotificationChannels()

        val rootLayout = findViewById<ConstraintLayout>(R.id.rootLayout)
        webView = findViewById(R.id.webView)
        layoutOsd = findViewById(R.id.layoutOsd)
        layoutOsdBanner = findViewById(R.id.layoutOsdBanner)
        layoutWebButtons = findViewById(R.id.layoutWebButtons)
        containerWebNotification = findViewById(R.id.containerWebNotification)

        btnMenu = findViewById(R.id.btnWebMenu)
        btnToggle = findViewById(R.id.btnWebToggle)
        btnClose = findViewById(R.id.btnWebClose)

        layoutScrollRight = findViewById(R.id.layoutScrollRight)
        btnScrollRightUp = findViewById(R.id.btnScrollRightUp)
        btnScrollRightDown = findViewById(R.id.btnScrollRightDown)

        layoutScreensaver = findViewById(R.id.layoutScreensaver)
        ivScreensaverLogo = findViewById(R.id.ivScreensaverLogo)
        val defaultScreensaverDrawable = ivScreensaverLogo.drawable

        isThumbnailEnabled = prefs.getBoolean("thumbnail_enabled_$hostIp", true)

        val thumbLayout = FrameLayout(this).apply {
            id = thumbContainerId
            visibility = View.GONE
            val size = (120 * resources.displayMetrics.density).toInt()
            layoutParams = ConstraintLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#00000000"))
            }
            clipToOutline = true
            isClickable = false
            isFocusable = false
        }

        webView3D = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.TRANSPARENT)
            isFocusable = false
            isClickable = false
            isFocusableInTouchMode = false

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val progressFloat = if (lastPrintState == "printing" || lastPrintState == "complete") lastProgressPercent.coerceIn(0.0, 1.0) else 1.0
                    view?.evaluateJavascript("if (window.updateBenchyProgress) window.updateBenchyProgress($progressFloat);", null)
                }
            }

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
            settings.blockNetworkImage = true
            settings.loadsImagesAutomatically = false
        }

        ivThumbBackground = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        ivThumbForeground = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        thumbLayout.addView(webView3D)
        thumbLayout.addView(ivThumbBackground)
        thumbLayout.addView(ivThumbForeground)
        rootLayout?.addView(thumbLayout)

        thumbLayout.apply { isClickable = false; isFocusable = false }
        layoutWebButtons.bringToFront()
        layoutOsd.bringToFront()
        layoutOsdBanner.bringToFront()
        layoutScrollRight.bringToFront()

        // Initialisierung aller externen Manager und des neuen MenuHelpers
        companionServerManager = CompanionServerManager(this, prefs, lifecycleScope, { command -> handleRemoteCommand(command) }, { hostIp }, { currentActiveUrl })
        thumbnailRenderHelper = ThumbnailRenderHelper { hostIp }
        screensaverManager = ScreensaverManager(layoutScreensaver, ivScreensaverLogo, defaultScreensaverDrawable, { thumbnailBitmap }, { lastPrintState }, { lastProgressPercent }, { currentGCodeFilename }, { deactivateScreensaver() })
        moonrakerPollManager = MoonrakerPollManager({ hostIp }, { currentActiveUrl }, { response -> handleMoonrakerResponse(response) })

        webViewMenuHelper = WebViewMenuHelper(this, prefs, hostIp, { isOsdEnabled }, { isThumbnailEnabled }) { action ->
            when (action) {
                is WebViewMenuHelper.MenuAction.CompanionMenu -> {
                    val settingsIntent = Intent(this, SettingsActivity::class.java).apply { putExtra("saved_menu_layer", 7) }
                    startActivity(settingsIntent)
                }
                is WebViewMenuHelper.MenuAction.DpadControl -> {
                    val remoteIntent = Intent(this, CompanionRemoteActivity::class.java)
                    startActivity(remoteIntent)
                }
                is WebViewMenuHelper.MenuAction.ToggleOsd -> {
                    isOsdEnabled = action.newState
                    prefs.edit().putBoolean("osd_enabled_$hostIp", isOsdEnabled).apply()
                    if (isOsdEnabled) {
                        val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                        if (osdStyle == "banner") { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.VISIBLE }
                        else { layoutOsd.visibility = View.VISIBLE; layoutOsdBanner.visibility = View.GONE }
                        moonrakerPollManager.startPolling(lifecycleScope) { hasTrigOffline }
                    } else { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE; moonrakerPollManager.stopPolling() }
                }
                is WebViewMenuHelper.MenuAction.ChangeOsdStyle -> {
                    showPillDialog(getSafeString("menu_osd_style_title", "OSD Stil"), arrayOf(getSafeString("osd_style_box", "Kompakte Box"), getSafeString("osd_style_banner", "Flexible Leiste"))) { styleIndex ->
                        prefs.edit().putString("osd_style_$hostIp", if (styleIndex == 0) "box" else "banner").apply()
                        applyOsdPositionAndStyle(prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center")
                    }
                }
                is WebViewMenuHelper.MenuAction.ZoomIn -> webView?.zoomIn()
                is WebViewMenuHelper.MenuAction.ZoomOut -> webView?.zoomOut()
                is WebViewMenuHelper.MenuAction.ToggleThumbnail -> {
                    isThumbnailEnabled = action.newState
                    prefs.edit().putBoolean("thumbnail_enabled_$hostIp", isThumbnailEnabled).apply()
                    val tView = findViewById<FrameLayout>(thumbContainerId)
                    if (isThumbnailEnabled && isCameraMode) { tView?.visibility = View.VISIBLE; setupProgressThumbnailDrawables(thumbnailBitmap) }
                    else { tView?.visibility = View.GONE }
                    applyOsdPositionAndStyle(prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center")
                }
                is WebViewMenuHelper.MenuAction.EnterPip -> enterPipMode()
                is WebViewMenuHelper.MenuAction.ToggleLight -> { isLightOn = !isLightOn; sendLightCommand(isLightOn) }
                is WebViewMenuHelper.MenuAction.ShowScreensaverConfig -> {
                    showScreensaverPillDialog(getSafeString("screensaver_title", "Timeout"), arrayOf(getSafeString("screensaver_now", "Jetzt"), "30 Min", "60 Min", "90 Min", "120 Min", getSafeString("screensaver_off", "Aus"))) { tIdx ->
                        val newTimeout = when (tIdx) {
                            0 -> { screensaverManager.setInstantActive(true); activateScreensaver(); return@showScreensaverPillDialog }
                            1 -> 30 * 60 * 1000L; 2 -> 60 * 60 * 1000L; 3 -> 90 * 60 * 1000L; 4 -> 120 * 60 * 1000L; else -> 0L
                        }
                        screensaverManager.setInstantActive(false); screensaverManager.setTimeout(newTimeout)
                        prefs.edit().putLong("screensaver_timeout_$hostIp", newTimeout).putLong("screensaver_timeout_global_fallback", newTimeout).apply()
                        resetInactivityTimer()
                    }
                }
                is WebViewMenuHelper.MenuAction.ChangeRatio -> {
                    showPillDialog(getSafeString("menu_ratio_title", "Format"), arrayOf("16:9", "4:3", "1:1")) { rIdx -> loadStreamOrUrl(currentActiveUrl, when(rIdx) { 0 -> 56.25f; 1 -> 75.0f; else -> 100.0f }) }
                }
                is WebViewMenuHelper.MenuAction.ChangeCameraType -> {
                    val localInitialPort = Uri.parse(currentActiveUrl).port.takeIf { it != -1 } ?: 7125
                    val savedDashboardPort = prefs.getInt("saved_dashboard_port_$hostIp", localInitialPort)
                    showPillDialog(getSafeString("menu_change_camera_type", "Kamera-Typ"), arrayOf("Web-Ansicht", "Stream Port 8080", "Webcam-Pfad")) { camIdx -> loadStreamOrUrl(when(camIdx) { 0 -> "http://$hostIp:$savedDashboardPort/camera.html"; 1 -> "http://$hostIp:8080/?action=stream"; else -> "http://$hostIp:$savedDashboardPort/webcam/?action=stream" }, 56.25f) }
                }
                is WebViewMenuHelper.MenuAction.EmergencyStop -> sendEmergencyStop()
            }
        }

        companionServerManager.startServers()

        rootLayout?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
                view.setPadding(0, insets.top, 0, 0)
                WindowInsetsCompat.CONSUMED
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

        tvOsdExtruderBanner = findViewById(R.id.tvOsdExtruderBanner)
        tvOsdBedBanner = findViewById(R.id.tvOsdBedBanner)
        tvOsdChamberSensorBanner = findViewById(R.id.tvOsdChamberSensorBanner)
        tvOsdChamberHeaterBanner = findViewById(R.id.tvOsdChamberHeaterBanner)
        tvOsdFanModelBanner = findViewById(R.id.tvOsdFanModelBanner)
        tvOsdFanAuxBanner = findViewById(R.id.tvOsdFanAuxBanner)
        tvOsdFanChamberBanner = findViewById(R.id.tvOsdFanChamberBanner)
        tvOsdProgressBanner = findViewById(R.id.tvOsdProgressBanner)
        tvOsdTimeBanner = findViewById(R.id.tvOsdTimeBanner)

        applySeichterOsdBackground()

        webView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (screensaverManager.isVisible()) { deactivateScreensaver() } else { resetInactivityTimer(); showButtons() }
            }
            false
        }

        webView?.isFocusable = true
        webView?.setOnKeyListener { _, keyCode, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { webView?.evaluateJavascript("window.scrollBy(0, 150);", null); showButtons(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (webView?.scrollY == 0) { btnToggle.requestFocus(); showButtons() }
                        else { webView?.evaluateJavascript("window.scrollBy(0, -120);", null); showButtons() }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> { webView?.evaluateJavascript("window.scrollBy(-150, 0);", null); showButtons(); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { webView?.evaluateJavascript("window.scrollBy(150, 0);", null); showButtons(); true }
                    else -> false
                }
            } else false
        }

        btnScrollRightUp.setOnClickListener { webView?.evaluateJavascript("window.scrollBy(0, -350);", null); showButtons() }
        btnScrollRightDown.setOnClickListener { webView?.evaluateJavascript("window.scrollBy(0, 350);", null); showButtons() }

        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                showButtons()
                view.animate().scaleX(1.1f).scaleY(1.1f).alpha(1.0f).translationZ(8f).setDuration(150).start()
                if (view is MaterialButton) { view.strokeWidth = 8; view.strokeColor = ColorStateList.valueOf(Color.parseColor("#4CAF50")) }
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.8f).translationZ(0f).setDuration(150).start()
                if (view is MaterialButton) view.strokeWidth = 0
            }
        }

        val allButtons = arrayOf(btnMenu, btnToggle, btnClose, btnScrollRightUp, btnScrollRightDown)
        allButtons.forEach { btn -> btn.isFocusable = true; btn.alpha = 0.8f; btn.onFocusChangeListener = tvFocusListener }
        arrayOf(btnMenu, btnToggle, btnClose).forEach { it.nextFocusUpId = R.id.webView }

        webView?.settings?.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
        }

        val printerIp = intent.getStringExtra("PRINTER_IP") ?: "127.0.0.1"
        val printerPort = intent.getStringExtra("PRINTER_PORT") ?: "7125"
        val isCameraDefault = intent.getBooleanExtra("IS_CAMERA_VIEW", false)

        if (currentActiveUrl.isEmpty()) {
            currentActiveUrl = if (isCameraDefault) "http://$printerIp:$printerPort/camera.html" else "http://$printerIp:$printerPort"
        }

        val savedRatio = try { prefs.getFloat("camera_ratio_$hostIp", 56.25f) } catch(e: Exception) { 56.25f }
        isOsdEnabled = prefs.getBoolean("osd_enabled_$hostIp", false)
        loadStreamOrUrl(currentActiveUrl, savedRatio)
        showButtons()

        val globalFallback = prefs.getLong("screensaver_timeout_global_fallback", 120 * 60 * 1000L)
        val savedTimeout = prefs.getLong("screensaver_timeout_$hostIp", globalFallback)
        screensaverManager.setTimeout(savedTimeout)
        if (savedTimeout > 0L) resetInactivityTimer()

        btnMenu.setOnClickListener {
            webViewMenuHelper.showMenuOptionsDialog(isCameraMode) { title, items, hexColors, onSelected ->
                showPillDialog(title, items, hexColors, onSelected)
            }
        }

        btnMenu.setOnLongClickListener {
            if (isCameraMode && isOsdEnabled) {
                val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                val positionOptions = if (osdStyle == "banner") arrayOf(getSafeString("osd_pos_banner_top", "Oben"), getSafeString("osd_pos_banner_bottom", "Unten"), getSafeString("osd_pos_banner_left", "Links"), getSafeString("osd_pos_banner_right", "Rechts")) else arrayOf("Oben Links", "Oben Mitte", "Oben Rechts", "Unten Mitte")
                showPillDialog(getSafeString("osd_position_title", "OSD Position"), positionOptions) { index -> val posStr = if (osdStyle == "banner") when(index) { 0 -> "top"; 1 -> "bottom"; 2 -> "left"; else -> "right" } else when(index) { 0 -> "top_left"; 1 -> "top_center"; 2 -> "top_right"; else -> "bottom_center" }; prefs.edit().putString("osd_position_$hostIp", posStr).apply(); applyOsdPositionAndStyle(posStr); showCenteredPillToast(getSafeString("toast_saved", "Gespeichert ✓")) }
                true
            } else { showCenteredPillToast(getSafeString("toast_fullscreen_only", "Nur im Vollbildmodus verfügbar")); true }
        }

        btnToggle.setOnClickListener {
            val sPort = prefs.getInt("saved_dashboard_port_$hostIp", Uri.parse(currentActiveUrl).port.takeIf { it != -1 } ?: 7125)
            if (isCameraMode) { loadStreamOrUrl("http://$hostIp:$sPort", 56.25f); showCenteredPillToast(getString(R.string.toast_loading_dashboard)) }
            else { loadStreamOrUrl(if (lastCameraUrl.isNotEmpty()) lastCameraUrl else "http://$hostIp:$sPort/camera.html", prefs.getFloat("camera_ratio_$hostIp", 56.25f)); showCenteredPillToast(getString(R.string.toast_loading_livestream)) }
        }

        btnToggle.setOnLongClickListener {
            if (isCameraMode) { showPillDialog("Standard-Port", arrayOf("Creality OS (4408)", "MainSail/Fluidd (7125)")) { whichDash -> prefs.edit().putInt("saved_dashboard_port_$hostIp", if (whichDash == 0) 4408 else 7125).apply(); loadStreamOrUrl("http://$hostIp:" + if (whichDash == 0) "4408" else "7125", 56.25f) }; true } else false
        }
        btnClose.setOnClickListener { finish() }
    }

    private fun resetInactivityTimer() {
        val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        screensaverManager.resetInactivityTimer(isCameraMode, isInPiP)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipActions() {
        val actions = ArrayList<RemoteAction>()
        val maxIntent = Intent(PIP_ACTION_MAXIMIZE).setPackage(packageName)
        val maxPendingIntent = PendingIntent.getBroadcast(this, PIP_REQUEST_CODE_MAXIMIZE, maxIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        actions.add(RemoteAction(Icon.createWithResource(this, android.R.drawable.ic_menu_revert), "Maximize", "Maximize", maxPendingIntent))

        val resizeIntent = Intent(PIP_ACTION_RESIZE).setPackage(packageName)
        val resizePendingIntent = PendingIntent.getBroadcast(this, PIP_REQUEST_CODE_RESIZE, resizeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        actions.add(RemoteAction(Icon.createWithResource(this, android.R.drawable.ic_menu_crop), "Resize", "Resize", resizePendingIntent))

        setPictureInPictureParams(PictureInPictureParams.Builder().setActions(actions).setAspectRatio(if (isPipWideRatio) Rational(16, 9) else Rational(1, 1)).build())
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())) {
                    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
                    if (uiModeManager.currentModeType != Configuration.UI_MODE_TYPE_TELEVISION) {
                        startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME); flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                    } else moveTaskToBack(true)
                }
            } catch (e: Exception) { showCenteredPillToast("PiP Error") }
        } else showCenteredPillToast("Not supported")
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
            screensaverManager.stopTimer()

            layoutWebButtons.visibility = View.GONE; layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE
            containerWebNotification?.visibility = View.GONE; layoutScreensaver.visibility = View.GONE; layoutScrollRight.visibility = View.GONE
            findViewById<FrameLayout>(thumbContainerId)?.visibility = View.GONE

            val lp = webView?.layoutParams as? ConstraintLayout.LayoutParams
            if (lp != null) { lp.dimensionRatio = null; lp.width = ConstraintLayout.LayoutParams.MATCH_PARENT; lp.height = ConstraintLayout.LayoutParams.MATCH_PARENT; webView?.layoutParams = lp }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipActions()

            pipReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    when (i?.action) {
                        PIP_ACTION_MAXIMIZE -> startActivity(Intent(this@WebViewActivity, WebViewActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT })
                        PIP_ACTION_RESIZE -> togglePipWindowSize()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, IntentFilter().apply { addAction(PIP_ACTION_MAXIMIZE); addAction(PIP_ACTION_RESIZE) }, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, IntentFilter().apply { addAction(PIP_ACTION_MAXIMIZE); addAction(PIP_ACTION_RESIZE) })
            }

            if (isOsdEnabled) moonrakerPollManager.startPolling(lifecycleScope) { hasTrigOffline }
        } else {
            try { pipReceiver?.let { unregisterReceiver(it) } } catch (e: Exception) {}
            pipReceiver = null

            layoutWebButtons.visibility = View.VISIBLE; containerWebNotification?.visibility = View.VISIBLE
            layoutScrollRight.visibility = if (isCameraMode) View.GONE else View.VISIBLE

            if (isOsdEnabled && isCameraMode) {
                val osdStyle = prefs.getString("osd_style_$hostIp", "box")
                if (osdStyle == "banner") layoutOsdBanner.visibility = View.VISIBLE else layoutOsd.visibility = View.VISIBLE
                moonrakerPollManager.startPolling(lifecycleScope) { hasTrigOffline }
            }
            if (isThumbnailEnabled && isCameraMode) findViewById<FrameLayout>(thumbContainerId)?.visibility = View.VISIBLE

            val lp = webView?.layoutParams as? ConstraintLayout.LayoutParams
            if (lp != null) {
                if (isCameraMode) {
                    lp.dimensionRatio = when (currentAspectRatioPercent) { 75.0f -> "H,4:3"; 100.0f -> "H,1:1"; else -> "H,16:9" }
                    lp.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT; lp.height = 0
                } else { lp.dimensionRatio = null; lp.width = ConstraintLayout.LayoutParams.MATCH_PARENT; lp.height = ConstraintLayout.LayoutParams.MATCH_PARENT }
                webView?.layoutParams = lp
            }
            showButtons(); resetInactivityTimer()
        }
    }

    override fun onStart() {
        super.onStart()
        isActivityInForeground = true
        if (isOsdEnabled && isCameraMode) moonrakerPollManager.startPolling(lifecycleScope) { hasTrigOffline }
    }

    override fun onResume() {
        super.onResume()
        val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (!isInPiP) { showButtons(); resetInactivityTimer(); btnToggle.post { btnToggle.requestFocus() } }
    }

    override fun onPause() { screensaverManager.stopTimer(); super.onPause() }
    override fun onStop() { isActivityInForeground = false; super.onStop() }
    override fun onUserInteraction() { super.onUserInteraction(); if (screensaverManager.isVisible()) deactivateScreensaver() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (isInPiP) return super.onKeyDown(keyCode, event)
        if (screensaverManager.isVisible()) { deactivateScreensaver(); return true }
        resetInactivityTimer()
        if (keyCode in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER)) showButtons()
        return super.onKeyDown(keyCode, event)
    }

    private fun activateScreensaver() {
        val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (isInPiP) return
        screensaverManager.stopTimer(); uiHandler.removeCallbacks(hideUiRunnable)

        layoutWebButtons.visibility = View.GONE; layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE
        layoutScrollRight.visibility = View.GONE; findViewById<FrameLayout>(thumbContainerId)?.visibility = View.GONE

        screensaverManager.activateScreensaver()
    }

    private fun deactivateScreensaver() {
        val fallback = prefs.getLong("screensaver_timeout_global_fallback", 120 * 60 * 1000L)
        screensaverManager.deactivateScreensaver(fallback)

        if (isOsdEnabled) {
            val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
            if (osdStyle == "banner") layoutOsdBanner.visibility = View.VISIBLE else layoutOsd.visibility = View.VISIBLE
        }
        if (!isCameraMode) layoutScrollRight.visibility = View.VISIBLE
        if (isThumbnailEnabled && isCameraMode) findViewById<FrameLayout>(thumbContainerId)?.visibility = View.VISIBLE
        showButtons(); btnToggle.requestFocus()
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val backgroundColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val textColorResource = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val pillView = TextView(this).apply {
            text = message; textSize = 16f; gravity = Gravity.CENTER; setTextColor(textColorResource)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 100f; setColor(backgroundColor); setStroke(4, textColorResource) }
            setPadding(toPx(50), toPx(35), toPx(50), toPx(35))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setMargins(toPx(50), 0, toPx(50), toPx(120)) }
        }
        val container = FrameLayout(this).apply { addView(pillView) }
        rootLayout.addView(container)
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2200)
    }

    private fun getProgressiveStepSpeed(speed: Double): Float {
        return if (speed <= 0.0) 0f else when { speed <= 25.0 -> 3.5f; speed <= 50.0 -> 9.5f; speed <= 75.0 -> 22.0f; else -> 48.0f }
    }

    private fun prepareRotatedFanIcon(context: Context, resId: Int, angle: Float, isNight: Boolean): Drawable? {
        val base = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        base.setColorFilter(if (isNight) Color.WHITE else Color.BLACK, PorterDuff.Mode.SRC_IN)
        val size = (18 * context.resources.displayMetrics.density).toInt()
        base.setBounds(0, 0, size, size)
        return RotateDrawable().apply { drawable = base; fromDegrees = angle; toDegrees = angle; pivotX = 0.5f; pivotY = 0.5f; level = 10000; setBounds(0, 0, size, size) }
    }

    private fun prepareOsdIcon(context: Context, resId: Int, isNight: Boolean): Drawable? {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return null
        drawable.setColorFilter(if (isNight) Color.WHITE else Color.BLACK, PorterDuff.Mode.SRC_IN)
        val size = (18 * context.resources.displayMetrics.density).toInt()
        drawable.setBounds(0, 0, size, size)
        return drawable
    }

    private fun showButtons() {
        val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        if (screensaverManager.isVisible() || isInPiP) return
        uiHandler.removeCallbacks(hideUiRunnable)
        if (layoutWebButtons.visibility != View.VISIBLE) layoutWebButtons.visibility = View.VISIBLE
        layoutWebButtons.animate().alpha(1f).setDuration(250).start()
        uiHandler.postDelayed(hideUiRunnable, immersiveTimeout)
    }

    private fun hideButtons() { layoutWebButtons.animate().alpha(0f).setDuration(500).start() }

    private fun applySeichterOsdBackground() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 24f; setColor(Color.parseColor(if (isNight) "#B3212529" else "#73FAFAFA")); setStroke(3, Color.parseColor(if (isNight) "#40FFFFFF" else "#33000000")) }
        layoutOsd.background = bg; layoutOsdBanner.background = bg
    }

    private fun resetPrintTriggers() {
        hasTrigFirstLayer = false; hasTrig50 = false; hasTrig75 = false; hasTrig90 = false; hasTrig100 = false; hasTrigOffline = false
        lastPrintState = ""; lastExtruderTemp = -1.0; lastExtruderTarget = -1.0; lastBedTemp = -1.0; lastBedTarget = -1.0
        lastChamberTemp = -1.0; lastChamberHeaterTemp = -1.0; lastChamberHeaterTarget = -1.0; lastFanModelSpeed = -1.0; lastFanAuxSpeed = -1.0; lastFanChamberSpeed = -1.0
        lastProgressPercent = -1.0; heartbeatTick = 0

        currentGCodeFilename = ""; thumbnailBitmap = null; clipDrawable = null
        ivThumbBackground?.setImageBitmap(null); ivThumbForeground?.setImageDrawable(null)
        webView3D?.visibility = View.GONE; webView3D?.loadUrl("about:blank"); isBenchyShowing = false
    }

    private fun sendLightCommand(turnOn: Boolean) {
        val hostIp = Uri.parse(currentActiveUrl).host ?: return
        val gcodeScript = "SET_PIN PIN=LED VALUE=" + if (turnOn) "1" else "0"
        lifecycleScope.launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null; var isSuccess = false
            try {
                conn = (URL("http://$hostIp:7125/printer/gcode/script").openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 2000; readTimeout = 2000; doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded") }
                conn.outputStream.use { it.write(("script=" + Uri.encode(gcodeScript)).toByteArray(Charsets.UTF_8)); it.flush() }
                if (conn.responseCode == 200) isSuccess = true
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
        val hostIp = Uri.parse(currentActiveUrl).host ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            var conn: HttpURLConnection? = null; var isSuccess = false; var code = -1
            try {
                conn = (URL("http://$hostIp:7125/printer/emergency_stop").openConnection() as HttpURLConnection).apply { requestMethod = "POST"; connectTimeout = 1500; readTimeout = 1500; setRequestProperty("Content-Length", "0") }
                code = conn.responseCode
                if (code == 200 || code == 204) isSuccess = true
            } catch (_: Exception) {} finally { conn?.disconnect() }
            withContext(Dispatchers.Main) {
                if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) {
                    if (isSuccess) showCenteredPillToast(getString(R.string.toast_stop_success))
                    else showCenteredPillToast(getString(R.string.toast_stop_error) + code)
                }
            }
        }
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        resetPrintTriggers()
        currentActiveUrl = url; currentAspectRatioPercent = paddingTopPercent
        isCameraMode = url.contains("action=stream") || url.contains("camera.html")
        val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
        layoutScrollRight.visibility = if (isCameraMode) View.GONE else View.VISIBLE

        if (isCameraMode) {
            lastCameraUrl = url
            if (isOsdEnabled) {
                if (osdStyle == "banner") { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.VISIBLE }
                else { layoutOsd.visibility = View.VISIBLE; layoutOsdBanner.visibility = View.GONE }
                moonrakerPollManager.startPolling(lifecycleScope) { hasTrigOffline }
            } else { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE }
            btnMenu.text = "≡"; btnMenu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            btnMenu.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#424242"))
            btnToggle.text = "⇄"

            val lp = webView?.layoutParams as? ConstraintLayout.LayoutParams
            if (lp != null) {
                lp.dimensionRatio = when (paddingTopPercent) { 75.0f -> "H,4:3"; 100.0f -> "H,1:1"; else -> "H,16:9" }
                lp.width = ConstraintLayout.LayoutParams.MATCH_CONSTRAINT; lp.height = 0
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID; lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                webView?.layoutParams = lp
            }
            setupProgressThumbnailDrawables(thumbnailBitmap)
        } else {
            layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE; moonrakerPollManager.stopPolling()
            findViewById<FrameLayout>(thumbContainerId)?.visibility = View.GONE
            btnMenu.text = "⚠"; btnMenu.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            btnMenu.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            btnToggle.text = "⇄"
            val lp = webView?.layoutParams as? ConstraintLayout.LayoutParams
            if (lp != null) { lp.dimensionRatio = null; lp.width = ConstraintLayout.LayoutParams.MATCH_PARENT; lp.height = ConstraintLayout.LayoutParams.MATCH_PARENT; webView?.layoutParams = lp }
        }
        webView?.loadUrl(url)
    }

    private fun setupProgressThumbnailDrawables(bitmap: android.graphics.Bitmap?) {
        val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        val tView = findViewById<FrameLayout>(thumbContainerId)

        if (bitmap != null) {
            isBenchyShowing = false; webView3D?.visibility = View.GONE; webView3D?.loadUrl("about:blank")
            ivThumbBackground?.visibility = View.VISIBLE; ivThumbForeground?.visibility = View.VISIBLE
            ivThumbBackground?.setImageBitmap(bitmap); ivThumbBackground?.alpha = 0.35f; ivThumbBackground?.rotation = 0f; ivThumbBackground?.rotationY = 0f

            val tintedDrawable = BitmapDrawable(resources, bitmap).apply { setColorFilter(Color.argb(140, 33, 150, 243), PorterDuff.Mode.SRC_ATOP) }
            val clip = ClipDrawable(tintedDrawable, Gravity.BOTTOM, ClipDrawable.VERTICAL)
            ivThumbForeground?.setImageDrawable(clip); this.clipDrawable = clip
            clip.level = (lastProgressPercent.coerceIn(0.0, 1.0) * 10000).toInt()
        } else {
            ivThumbBackground?.visibility = View.GONE; ivThumbForeground?.visibility = View.GONE
            ivThumbBackground?.setImageDrawable(null); ivThumbForeground?.setImageDrawable(null); this.clipDrawable = null
            webView3D?.visibility = View.VISIBLE
            if (!isBenchyShowing) { isBenchyShowing = true; webView3D?.loadUrl("file:///android_asset/benchy.html") }
            webView3D?.evaluateJavascript("if (window.updateBenchyProgress) window.updateBenchyProgress(${if (lastPrintState == "printing" || lastPrintState == "complete") lastProgressPercent.coerceIn(0.0, 1.0) else 1.0});", null)
        }
        if (isThumbnailEnabled && isCameraMode && !isInPiP) tView?.visibility = View.VISIBLE else tView?.visibility = View.GONE
        applyOsdPositionAndStyle(prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center")
    }

    private fun handleMoonrakerResponse(responseText: String) {
        if (responseText.isEmpty()) {
            if (!isInPictureInPictureMode && isActivityInForeground) {
                if (tvOsdProgress?.text != getString(R.string.osd_printer_offline)) {
                    tvOsdProgress?.text = getString(R.string.osd_printer_offline); tvOsdProgress?.setTextColor(Color.parseColor("#E53935")); tvOsdTime?.text = ""
                    tvOsdProgressBanner?.text = getString(R.string.osd_printer_offline); tvOsdProgressBanner?.setTextColor(Color.parseColor("#E53935")); tvOsdTimeBanner?.text = ""
                }
            }
            if (!hasTrigOffline) { hasTrigOffline = true; triggerPopup("offline", "Offline", "Offline") }
            return
        }

        if (hasTrigOffline) { hasTrigOffline = false; webView?.post { webView?.reload() } }
        hasTrigOffline = false

        heartbeatTick = if (heartbeatTick >= 5) 1 else heartbeatTick + 1
        try {
            val json = JSONObject(responseText)
            val status = json.optJSONObject("result")?.optJSONObject("status") ?: return
            if (heartbeatTick == 1 && (moonrakerPollManager.knownChamberSensor == "temperature_sensor chamber_temp" || moonrakerPollManager.knownChamberHeater == "heater_generic chamber_heater")) {
                moonrakerPollManager.runChamberAutoSearch(status)
            }
            val ledPinObj = status.optJSONObject("output_pin LED")
            if (ledPinObj != null) isLightOn = ledPinObj.optDouble("value", 0.0) > 0.0

            val extruder = status.optJSONObject("extruder")
            val tempExtruder = extruder?.optDouble("temperature", 0.0) ?: 0.0
            val targetExtruder = extruder?.optDouble("target", 0.0) ?: 0.0
            val bed = status.optJSONObject("heater_bed")
            val tempBed = bed?.optDouble("temperature", 0.0) ?: 0.0
            val targetBed = bed?.optDouble("target", 0.0) ?: 0.0
            val tempChamber = status.optJSONObject(moonrakerPollManager.knownChamberSensor ?: "")?.optDouble("temperature", 0.0) ?: 0.0
            val chamberHeaterObj = status.optJSONObject(moonrakerPollManager.knownChamberHeater ?: "")
            val tempChamberHeater = chamberHeaterObj?.optDouble("temperature", 0.0) ?: 0.0
            val targetChamberHeater = chamberHeaterObj?.optDouble("target", 0.0) ?: 0.0

            var fanModelSpeed = 0.0
            val fan0Obj = status.optJSONObject("output_pin fan0")
            if (fan0Obj != null) fanModelSpeed = (if (fan0Obj.has("value")) fan0Obj.optDouble("value", 0.0) else fan0Obj.optDouble("speed", 0.0)) * 100.0
            var fanAuxSpeed = 0.0
            val fan2Obj = status.optJSONObject("output_pin fan2")
            if (fan2Obj != null) fanAuxSpeed = (if (fan2Obj.has("value")) fan2Obj.optDouble("value", 0.0) else fan2Obj.optDouble("speed", 0.0)) * 100.0
            var fanChamberSpeed = 0.0
            if (status.has("temperature_fan chamber_fan")) { val obj = status.optJSONObject("temperature_fan chamber_fan"); if (obj != null) fanChamberSpeed = (if (obj.has("speed")) obj.optDouble("speed", 0.0) else obj.optDouble("value", 0.0)) * 100.0 }
            else if (status.has("heater_fan chamber_fan")) { val obj = status.optJSONObject("heater_fan chamber_fan"); if (obj != null) fanChamberSpeed = (if (obj.has("value")) obj.optDouble("value", 0.0) else obj.optDouble("speed", 0.0)) * 100.0 }

            val displayStatus = status.optJSONObject("display_status")
            var progress = displayStatus?.optDouble("progress", 0.0) ?: 0.0
            val displayMessage = displayStatus?.optString("message", "") ?: ""

            if (progress == 0.0 && !displayMessage.isNullOrEmpty()) {
                try {
                    val match = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(displayMessage)
                    if (match.find()) { val parsedNum = match.group(1)?.toDoubleOrNull(); if (parsedNum != null && parsedNum <= 100.0) progress = parsedNum / 100.0 }
                } catch (_: Exception) {}
            }

            val printStats = status.optJSONObject("print_stats")
            val currentState = printStats?.optString("state", "") ?: ""
            val filename = printStats?.optString("filename", "") ?: ""
            val duration = printStats?.optInt("print_duration", 0) ?: 0
            lastProgressPercent = progress

            if (currentState == "printing" && filename.isNotEmpty() && filename != currentGCodeFilename) {
                currentGCodeFilename = filename
                lifecycleScope.launch {
                    val bitmap = thumbnailRenderHelper.fetchGCodeThumbnailMetadata(filename)
                    if (!this@WebViewActivity.isFinishing && !this@WebViewActivity.isDestroyed) { thumbnailBitmap = bitmap; setupProgressThumbnailDrawables(thumbnailBitmap) }
                }
            }

            if (currentState == "printing" && (lastPrintState in listOf("standby", "complete") || lastPrintState.isEmpty())) { hasTrigFirstLayer = false; hasTrig50 = false; hasTrig75 = false; hasTrig90 = false; hasTrig100 = false }
            val stateChanged = (lastPrintState != currentState)
            lastPrintState = currentState

            if (currentState == "printing" || currentState == "complete") {
                if (!hasTrigFirstLayer && (progress >= 0.015 || duration >= 90)) { hasTrigFirstLayer = true; triggerPopup("first_layer", "First Layer", "First Layer") }
                if (!hasTrig50 && progress >= 0.50) { hasTrig50 = true; triggerPopup("50", "50%", "50%") }
                if (!hasTrig75 && progress >= 0.75) { hasTrig75 = true; triggerPopup("75", "75%", "75%") }
                if (!hasTrig90 && progress >= 0.90) { hasTrig90 = true; triggerPopup("90", "90%", "90%") }
                if (!hasTrig100 && (currentState == "complete" || progress >= 0.999)) { hasTrig100 = true; triggerPopup("100", "100%", "100%") }
            }

            val tView = findViewById<FrameLayout>(thumbContainerId)
            val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
            val shouldShowThumb = isThumbnailEnabled && isCameraMode && !isInPiP

            if (currentState == "printing" || currentState == "complete") {
                if (thumbnailBitmap != null) clipDrawable?.level = (progress * 10000).toInt()
                else webView3D?.evaluateJavascript("if (window.updateBenchyProgress) window.updateBenchyProgress(${progress.coerceIn(0.0, 1.0)});", null)
                if (tView != null) { tView.visibility = if (shouldShowThumb) View.VISIBLE else View.GONE; if (stateChanged && thumbnailBitmap == null) setupProgressThumbnailDrawables(null) }
            } else {
                currentGCodeFilename = ""
                if (thumbnailBitmap != null || !isBenchyShowing) { thumbnailBitmap = null; setupProgressThumbnailDrawables(null) }
                webView3D?.evaluateJavascript("if (window.updateBenchyProgress) window.updateBenchyProgress(1.0);", null)
                if (tView != null) tView.visibility = if (shouldShowThumb) View.VISIBLE else View.GONE
            }

            if (!isInPictureInPictureMode && isActivityInForeground) {
                val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                val savedPos = prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center"
                val divider = if (osdStyle == "banner" && savedPos != "left" && savedPos != "right") "  •  " else ""
                val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                val boxViews = arrayOf(tvOsdExtruder, tvOsdBed, tvOsdChamberSensor, tvOsdChamberHeater, tvOsdFanModel, tvOsdFanAux, tvOsdFanChamber, tvOsdProgress, tvOsdTime)
                val bannerViews = arrayOf(tvOsdExtruderBanner, tvOsdBedBanner, tvOsdChamberSensorBanner, tvOsdChamberHeaterBanner, tvOsdFanModelBanner, tvOsdFanAuxBanner, tvOsdFanChamberBanner, tvOsdProgressBanner, tvOsdTimeBanner)

                boxViews.forEach { tv -> tv?.setTextColor(if (isNight) Color.WHITE else Color.BLACK) }
                bannerViews.forEach { tv -> tv?.setTextColor(if (isNight) Color.WHITE else Color.BLACK) }

                tvOsdExtruder?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.fluid_24, isNight), null, null, null)
                tvOsdBed?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.windshield_defrost_rear_24, isNight), null, null, null)
                tvOsdChamberSensor?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.bottom_navigation_24, isNight), null, null, null)
                tvOsdChamberHeater?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.thermostat_24, isNight), null, null, null)
                tvOsdExtruderBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.fluid_24, isNight), null, null, null)
                tvOsdBedBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.windshield_defrost_rear_24, isNight), null, null, null)
                tvOsdChamberSensorBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.bottom_navigation_24, isNight), null, null, null)
                tvOsdChamberHeaterBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.thermostat_24, isNight), null, null, null)

                if (fanModelSpeed > 0.0) fanModelRotationAngle = (fanModelRotationAngle + getProgressiveStepSpeed(fanModelSpeed)) % 360f
                if (fanAuxSpeed > 0.0) fanAuxRotationAngle = (fanAuxRotationAngle - getProgressiveStepSpeed(fanAuxSpeed)) % 360f
                if (fanChamberSpeed > 0.0) fanChamberRotationAngle = (fanChamberRotationAngle + getProgressiveStepSpeed(fanChamberSpeed)) % 360f

                tvOsdFanModel?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanModelRotationAngle, isNight), null, null, null)
                tvOsdFanAux?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanAuxRotationAngle, isNight), null, null, null)
                tvOsdFanChamber?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanChamberRotationAngle, isNight), null, null, null)
                tvOsdFanModelBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanModelRotationAngle, isNight), null, null, null)
                tvOsdFanAuxBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanAuxRotationAngle, isNight), null, null, null)
                tvOsdFanChamberBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareRotatedFanIcon(this, R.drawable.mode_fan_24, fanChamberRotationAngle, isNight), null, null, null)

                tvOsdProgress?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.ic_progress_clock, isNight), null, null, null)
                tvOsdTime?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.hourglass_top_24, isNight), null, null, null)
                tvOsdProgressBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.ic_progress_clock, isNight), null, null, null)
                tvOsdTimeBanner?.setCompoundDrawablesWithIntrinsicBounds(prepareOsdIcon(this, R.drawable.hourglass_top_24, isNight), null, null, null)

                val padPx = (6 * resources.displayMetrics.density).toInt()
                boxViews.forEach { it?.compoundDrawablePadding = padPx }; bannerViews.forEach { it?.compoundDrawablePadding = padPx }
                val boxDivider = if ((layoutOsd as? LinearLayout)?.orientation == LinearLayout.HORIZONTAL) "  •  " else ""

                val extStr = getSafeString("osd_extruder", "Extruder: %.1f° / %.1f°")
                val bedStr = getSafeString("osd_bed", "Bett: %.1f° / %.1f°")
                val chSnsStr = getSafeString("osd_chamber_sensor", "Kammer: %.1f°")
                val chHtrStr = getSafeString("osd_chamber_heater", "K-Heizung: %.1f° / %.1f°")
                val fanMdlStr = getSafeString("osd_fan_model", "Lüfter: %.0f%%")
                val fanAuxStr = getSafeString("osd_fan_aux", "Aux: %.0f%%")
                val fanChmStr = getSafeString("osd_fan_chamber", "K-Lüfter: %.0f%%")
                val prgStr = getSafeString("osd_progress_label", "Fortschritt: %s")
                val timeStr = getSafeString("osd_time", "Zeit: %s / %s")

                tvOsdExtruder?.text = " " + String.format(extStr, tempExtruder, targetExtruder) + boxDivider
                tvOsdBed?.text = " " + String.format(bedStr, tempBed, targetBed) + boxDivider
                tvOsdChamberSensor?.visibility = View.VISIBLE; tvOsdChamberSensor?.text = " " + String.format(chSnsStr, tempChamber) + boxDivider
                tvOsdChamberHeater?.visibility = View.VISIBLE; tvOsdChamberHeater?.text = " " + String.format(chHtrStr, tempChamberHeater, targetChamberHeater) + boxDivider
                tvOsdFanModel?.text = " " + String.format(fanMdlStr, fanModelSpeed) + boxDivider
                tvOsdFanAux?.text = " " + String.format(fanAuxStr, fanAuxSpeed) + boxDivider
                tvOsdFanChamber?.text = " " + String.format(fanChmStr, fanChamberSpeed)
                val progressText = String.format(Locale.getDefault(), "%.1f%%", progress * 100)
                tvOsdProgress?.text = " " + String.format(prgStr, progressText) + boxDivider
                val timeDigits = String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60)
                tvOsdTime?.text = " " + String.format(timeStr, timeDigits, "--:--") + " " + ".".repeat(heartbeatTick)

                tvOsdExtruderBanner?.text = " " + String.format(extStr, tempExtruder, targetExtruder) + divider
                tvOsdBedBanner?.text = " " + String.format(bedStr, tempBed, targetBed) + divider
                tvOsdChamberSensorBanner?.visibility = View.VISIBLE; tvOsdChamberSensorBanner?.text = " " + String.format(chSnsStr, tempChamber) + divider
                tvOsdChamberHeaterBanner?.visibility = View.VISIBLE; tvOsdChamberHeaterBanner?.text = " " + String.format(chHtrStr, tempChamberHeater, targetChamberHeater) + divider
                tvOsdFanModelBanner?.text = " " + String.format(fanMdlStr, fanModelSpeed) + divider
                tvOsdFanAuxBanner?.text = " " + String.format(fanAuxStr, fanAuxSpeed) + divider
                tvOsdFanChamberBanner?.text = " " + String.format(fanChmStr, fanChamberSpeed) + divider
                tvOsdProgressBanner?.text = " " + String.format(prgStr, progressText) + divider
                tvOsdTimeBanner?.text = " " + String.format(timeStr, timeDigits, "--:--") + " " + ".".repeat(heartbeatTick)

                layoutOsd.invalidate(); layoutOsdBanner.invalidate()
            }
        } catch (_: Exception) {}
    }

    private fun applyOsdPositionAndStyle(position: String) {
        val root = findViewById<ConstraintLayout>(R.id.rootLayout) ?: return
        val boxContainer = layoutOsd as? LinearLayout ?: return
        val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
        val osdBannerFlow = findViewById<androidx.constraintlayout.helper.widget.Flow>(R.id.osdBannerFlow)

        if (isOsdEnabled && isCameraMode) {
            if (osdStyle == "banner") { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.VISIBLE }
            else { layoutOsd.visibility = View.VISIBLE; layoutOsdBanner.visibility = View.GONE }
        } else { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE }

        val constraintSet = ConstraintSet()
        constraintSet.clone(root)
        val targetLayoutId = if (osdStyle == "banner") R.id.layoutOsdBanner else R.id.layoutOsd
        val hiddenLayoutId = if (osdStyle == "banner") R.id.layoutOsd else R.id.layoutOsdBanner

        arrayOf(targetLayoutId, hiddenLayoutId, thumbContainerId).forEach { id ->
            constraintSet.clear(id, ConstraintSet.TOP); constraintSet.clear(id, ConstraintSet.BOTTOM)
            constraintSet.clear(id, ConstraintSet.START); constraintSet.clear(id, ConstraintSet.END)
        }

        val marginHorizontal = 40; val marginTopBottom = 30
        if (osdStyle == "banner") {
            when (position) {
                "top" -> { constraintSet.connect(targetLayoutId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginHorizontal); constraintSet.connect(targetLayoutId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, marginHorizontal); constraintSet.connect(targetLayoutId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom); constraintSet.constrainWidth(targetLayoutId, ConstraintSet.MATCH_CONSTRAINT); constraintSet.constrainHeight(targetLayoutId, ConstraintSet.WRAP_CONTENT); osdBannerFlow?.setOrientation(0) }
                "left" -> { constraintSet.connect(targetLayoutId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginHorizontal); constraintSet.connect(targetLayoutId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom); constraintSet.connect(targetLayoutId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.constrainWidth(targetLayoutId, ConstraintSet.WRAP_CONTENT); constraintSet.constrainHeight(targetLayoutId, ConstraintSet.MATCH_CONSTRAINT); osdBannerFlow?.setOrientation(1) }
                "right" -> { constraintSet.connect(targetLayoutId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, marginHorizontal); constraintSet.connect(targetLayoutId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom); constraintSet.connect(targetLayoutId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.constrainWidth(targetLayoutId, ConstraintSet.WRAP_CONTENT); constraintSet.constrainHeight(targetLayoutId, ConstraintSet.MATCH_CONSTRAINT); osdBannerFlow?.setOrientation(1) }
                else -> { constraintSet.connect(targetLayoutId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginHorizontal); constraintSet.connect(targetLayoutId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, marginHorizontal); constraintSet.connect(targetLayoutId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.constrainWidth(targetLayoutId, ConstraintSet.MATCH_CONSTRAINT); constraintSet.constrainHeight(targetLayoutId, ConstraintSet.WRAP_CONTENT); osdBannerFlow?.setOrientation(0) }
            }
        } else {
            constraintSet.constrainWidth(targetLayoutId, ConstraintSet.WRAP_CONTENT); constraintSet.constrainHeight(targetLayoutId, ConstraintSet.WRAP_CONTENT)
            when (position) {
                "top_left" -> { constraintSet.connect(targetLayoutId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom); constraintSet.connect(targetLayoutId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, marginHorizontal); boxContainer.orientation = LinearLayout.VERTICAL }
                "top_center" -> { constraintSet.connect(targetLayoutId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom); constraintSet.connect(targetLayoutId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START); constraintSet.connect(targetLayoutId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END); boxContainer.orientation = LinearLayout.HORIZONTAL }
                "top_right" -> { constraintSet.connect(targetLayoutId, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, marginTopBottom); constraintSet.connect(targetLayoutId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, marginHorizontal); boxContainer.orientation = LinearLayout.VERTICAL }
                else -> { constraintSet.connect(targetLayoutId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.connect(targetLayoutId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START); constraintSet.connect(targetLayoutId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END); boxContainer.orientation = LinearLayout.HORIZONTAL }
            }
        }

        if (isThumbnailEnabled && isCameraMode) {
            constraintSet.constrainWidth(thumbContainerId, (120 * resources.displayMetrics.density).toInt()); constraintSet.constrainHeight(thumbContainerId, (120 * resources.displayMetrics.density).toInt())
            if (osdStyle == "banner") {
                when (position) {
                    "bottom" -> { constraintSet.connect(thumbContainerId, ConstraintSet.BOTTOM, targetLayoutId, ConstraintSet.TOP, 30); constraintSet.connect(thumbContainerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 40) }
                    "left" -> { constraintSet.connect(thumbContainerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.connect(thumbContainerId, ConstraintSet.START, targetLayoutId, ConstraintSet.END, 30) }
                    else -> { constraintSet.connect(thumbContainerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.connect(thumbContainerId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 40) }
                }
            } else { constraintSet.connect(thumbContainerId, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 140); constraintSet.connect(thumbContainerId, ConstraintSet.START, android.R.id.content, ConstraintSet.START, 40) }
        } else constraintSet.setVisibility(thumbContainerId, ConstraintSet.GONE)

        constraintSet.applyTo(root)
        updateOsdTextForm(osdStyle == "banner" || boxContainer.orientation == LinearLayout.HORIZONTAL)
    }

    private fun updateOsdTextForm(isSingleLine: Boolean) {
        val views = arrayOf(tvOsdExtruder, tvOsdBed, tvOsdChamberSensor, tvOsdChamberHeater, tvOsdFanModel, tvOsdFanAux, tvOsdFanChamber, tvOsdProgress, tvOsdTime)
        views.forEach { tv ->
            tv?.let {
                val lp = it.layoutParams as LinearLayout.LayoutParams
                if (isSingleLine) { lp.width = LinearLayout.LayoutParams.WRAP_CONTENT; lp.setMargins(16, 4, 16, 4) }
                else { lp.width = LinearLayout.LayoutParams.MATCH_PARENT; lp.setMargins(8, 2, 8, 2) }
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
                text = itemText; isAllCaps = false; textSize = 16f; cornerRadius = 100
                this.setPadding(0, toPx(14), 0, toPx(14))
                if (customHex != null) { backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex)); setTextColor(Color.WHITE) }
                else { backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isNight) "#33FFFFFF" else "#1A888888")); setTextColor(if (isNight) Color.WHITE else Color.BLACK) }
                isFocusable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(10), 0, toPx(10)) }
                onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        view.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        (view as? MaterialButton)?.strokeWidth = 8; (view as? MaterialButton)?.strokeColor = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    } else { view.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start(); (view as? MaterialButton)?.strokeWidth = 0 }
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
        val activeTimeout = prefs.getLong("screensaver_timeout_global_fallback", 120 * 60 * 1000L)

        items.forEachIndexed { index, itemText ->
            val targetMs = when (index) { 1 -> 30 * 60 * 1000L; 2 -> 60 * 60 * 1000L; 3 -> 90 * 60 * 1000L; 4 -> 120 * 60 * 1000L; else -> -1L }
            val isCurrentlyActive = (targetMs == activeTimeout) || (index == 5 && activeTimeout == 0L)
            val btn = MaterialButton(this).apply {
                text = itemText; isAllCaps = false; textSize = 16f; cornerRadius = 100
                this.setPadding(0, toPx(14), 0, toPx(14))
                if (isCurrentlyActive) { backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")); setTextColor(Color.WHITE) }
                else { backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isNight) "#33FFFFFF" else "#1A888888")); setTextColor(if (isNight) Color.WHITE else Color.BLACK) }
                isFocusable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }
                onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                    if (hasFocus) {
                        view.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        (view as? MaterialButton)?.strokeWidth = 8; (view as? MaterialButton)?.strokeColor = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    } else { view.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start(); (view as? MaterialButton)?.strokeWidth = 0 }
                }
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }
            container?.addView(btn)
        }
        dialog.show()
    }

    private fun handleRemoteCommand(command: String) {
        if (isFinishing || isDestroyed) return

        if (screensaverManager.isVisible()) {
            deactivateScreensaver()
        }
        resetInactivityTimer()
        showButtons()
        webView?.requestFocus()

        when (command) {
            "DPAD_UP" -> sendLocalKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
            "DPAD_DOWN" -> sendLocalKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
            "DPAD_LEFT" -> sendLocalKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
            "DPAD_RIGHT" -> sendLocalKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
            "DPAD_OK" -> sendLocalKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
            "BACK" -> sendLocalKeyEvent(KeyEvent.KEYCODE_BACK)

            "ZOOM_IN" -> webView?.zoomIn()
            "ZOOM_OUT" -> webView?.zoomOut()

            "PIP_TOGGLE" -> {
                val isInPiP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
                if (isInPiP) {
                    val maxIntent = Intent(this, WebViewActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT }
                    startActivity(maxIntent)
                } else { enterPipMode() }
            }
            "TOGGLE_VIDEO" -> { btnToggle.performClick() }
            "TOGGLE_OSD" -> {
                isOsdEnabled = !isOsdEnabled
                prefs.edit().putBoolean("osd_enabled_$hostIp", isOsdEnabled).apply()
                if (isOsdEnabled) {
                    val osdStyle = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                    if (osdStyle == "banner") { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.VISIBLE }
                    else { layoutOsd.visibility = View.VISIBLE; layoutOsdBanner.visibility = View.GONE }
                    moonrakerPollManager.startPolling(lifecycleScope) { hasTrigOffline }
                } else { layoutOsd.visibility = View.GONE; layoutOsdBanner.visibility = View.GONE; moonrakerPollManager.stopPolling() }
            }
            "STYLE_BOX" -> {
                prefs.edit().putString("osd_style_$hostIp", "box").apply()
                applyOsdPositionAndStyle(prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center")
            }
            "STYLE_BANNER" -> {
                prefs.edit().putString("osd_style_$hostIp", "banner").apply()
                applyOsdPositionAndStyle(prefs.getString("osd_position_$hostIp", "bottom_center") ?: "bottom_center")
            }
            "SEC_UP" -> {
                val style = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                val targetPos = if (style == "banner") "top" else "top_center"
                prefs.edit().putString("osd_position_$hostIp", targetPos).apply()
                applyOsdPositionAndStyle(targetPos)
            }
            "SEC_DOWN" -> {
                val style = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                val targetPos = if (style == "banner") "bottom" else "bottom_center"
                prefs.edit().putString("osd_position_$hostIp", targetPos).apply()
                applyOsdPositionAndStyle(targetPos)
            }
            "SEC_LEFT" -> {
                val style = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                val targetPos = if (style == "banner") "left" else "top_left"
                prefs.edit().putString("osd_position_$hostIp", targetPos).apply()
                applyOsdPositionAndStyle(targetPos)
            }
            "SEC_RIGHT" -> {
                val style = prefs.getString("osd_style_$hostIp", "box") ?: "box"
                val targetPos = if (style == "banner") "right" else "top_right"
                prefs.edit().putString("osd_position_$hostIp", targetPos).apply()
                applyOsdPositionAndStyle(targetPos)
            }
            "CAMERA_CYCLE" -> {
                val savedDashboardPort = prefs.getInt("saved_dashboard_port_$hostIp", 7125)
                val currentMode = when {
                    currentActiveUrl.contains("action=stream") && currentActiveUrl.contains("8080") -> 1
                    currentActiveUrl.contains("webcam/?action=stream") -> 2
                    else -> 0
                }
                val nextMode = (currentMode + 1) % 3
                val nextUrl = when (nextMode) {
                    1 -> "http://$hostIp:8080/?action=stream"
                    2 -> "http://$hostIp:$savedDashboardPort/webcam/?action=stream"
                    else -> "http://$hostIp:$savedDashboardPort/camera.html"
                }
                loadStreamOrUrl(nextUrl, 56.25f)
            }
            "LIGHT" -> { isLightOn = !isLightOn; sendLightCommand(isLightOn) }
            "ESTOP" -> sendEmergencyStop()
        }
    }

    private fun sendLocalKeyEvent(keyCode: Int) {
        runOnUiThread {
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    override fun onDestroy() {
        companionServerManager.stopServers()
        moonrakerPollManager.stopPolling()
        screensaverManager.destroy()
        uiHandler.removeCallbacks(hideUiRunnable)
        try { pipReceiver?.let { unregisterReceiver(it) } } catch (_: Exception) {}
        pipReceiver = null
        webView3D?.let { (it.parent as? ViewGroup)?.removeView(it); it.removeAllViews(); it.destroy() }
        webView3D = null
        webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.removeAllViews(); it.destroy() }
        webView = null
        super.onDestroy()
    }
}