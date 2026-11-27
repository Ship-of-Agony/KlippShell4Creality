package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class CompanionRemoteActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etTargetTvIp: EditText
    private lateinit var tvRemoteStatus: TextView
    private lateinit var btnSearchTv: MaterialButton
    private lateinit var tvFooterInfo: TextView
    private lateinit var btnLeaveRemote: MaterialButton

    private lateinit var btnRemoteUp: MaterialButton
    private lateinit var btnRemoteDown: MaterialButton
    private lateinit var btnRemoteLeft: MaterialButton
    private lateinit var btnRemoteRight: MaterialButton
    private lateinit var btnRemoteOk: MaterialButton
    private lateinit var btnRemoteBackGlobal: MaterialButton

    private lateinit var btnRemoteHome: MaterialButton
    private lateinit var btnRemoteStream: MaterialButton
    private lateinit var btnRemoteWebMenu: MaterialButton
    private lateinit var btnRemoteCamera: MaterialButton
    private lateinit var btnRemoteEstop: MaterialButton
    private lateinit var btnRemoteZoomIn: MaterialButton
    private lateinit var btnRemoteZoomOut: MaterialButton

    private lateinit var btnRemoteLayout: MaterialButton
    private lateinit var btnRemotePip: MaterialButton
    private lateinit var btnRemoteBar: MaterialButton
    private lateinit var btnRemoteBox: MaterialButton

    private var btnRemoteSecUp: MaterialButton? = null
    private var btnRemoteSecDown: MaterialButton? = null
    private var btnRemoteSecLeft: MaterialButton? = null
    private var btnRemoteSecRight: MaterialButton? = null

    private lateinit var layoutDpadZone: ViewGroup
    private lateinit var layoutTouchZone: ViewGroup

    private lateinit var viewTouchPadField: View
    private lateinit var btnTouchBack: MaterialButton
    private lateinit var btnTouchReturnDpad: MaterialButton
    private lateinit var btnTouchOk: MaterialButton
    private lateinit var btnToggleTouchMode: MaterialButton

    private var targetMasterIp: String = ""
    private var isConnected = false

    // Persistente TCP-Leitung & FIFO-Befehlswarteschlange
    private var sessionJob: Job? = null
    private var discoveryJob: Job? = null
    private var commandChannel = Channel<String>(Channel.UNLIMITED)
    private var nsdManager: NsdManager? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    private var isVideoMode = false
    private var isOsdOn = false
    private var isPipMode = false
    private var isWebMenuOpen = false
    private var currentOsdStyle = "box"

    private var isTouchModeActive = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var pendingDeltaX = 0f
    private var pendingDeltaY = 0f
    private var lastMoveSentTime = 0L
    private var wasTwoFingerGesture = false

    private val hintHandler = Handler(Looper.getMainLooper())
    private var activeHintView: View? = null
    private val hideHintRunnable = Runnable { dismissLongPressHint() }

    private val lastCommandSentAt = HashMap<String, Long>()
    private var lastHintShownAt = 0L

    private val isNightMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private val targetBorderColor: Int
        get() = if (isNightMode) Color.parseColor("#4CAF50") else Color.parseColor("#424242")

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "system") ?: "system"
        val config = Configuration(newBase.resources.configuration)

        if (savedLang != "system") {
            val locale = Locale.forLanguageTag(savedLang)
            Locale.setDefault(locale)
            config.setLocale(locale)
        }

        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> {
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            AppCompatDelegate.MODE_NIGHT_NO -> {
                config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            }
        }

        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_companion_remote)

        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        targetMasterIp = prefs.getString("last_master_tv_ip", "") ?: ""

        isVideoMode = prefs.getBoolean("remote_state_video", false)
        isOsdOn = prefs.getBoolean("remote_state_osd", false)
        isPipMode = prefs.getBoolean("remote_state_pip", false)
        currentOsdStyle = prefs.getString("remote_state_style", "box") ?: "box"

        initViews()
        setupListeners()
        setupSpecialToggles()
        setupLongPressDescriptions()
        applyUnifiedButtonShapesAndFocus()

        refreshOsdDependentUi()
        applyFloatingBenchyBackground()
        applyDynamicVignetteBorder()
        checkCompanionModeLockState()

        val widgetIpOverride = intent.getStringExtra("TARGET_WIDGET_IP")
        if (!widgetIpOverride.isNullOrEmpty()) {
            targetMasterIp = widgetIpOverride
            etTargetTvIp.setText(widgetIpOverride)
            startPersistentSession(widgetIpOverride)
        } else if (targetMasterIp.isNotEmpty()) {
            etTargetTvIp.setText(targetMasterIp)
            startPersistentSession(targetMasterIp)
        } else {
            discoverAndConnectTvAuto()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isConnected && targetMasterIp.isNotEmpty() && sessionJob?.isActive != true) {
            startPersistentSession(targetMasterIp)
        }
    }

    override fun onPause() {
        stopNsdDiscovery()
        dismissLongPressHint()
        super.onPause()
    }

    private fun toPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun getLocalizedHint(stringResName: String, fallback: String): String {
        return try {
            val id = resources.getIdentifier(stringResName, "string", packageName)
            if (id != 0) getString(id) else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun dismissLongPressHint() {
        hintHandler.removeCallbacks(hideHintRunnable)
        activeHintView?.let { hintView ->
            (hintView.parent as? ViewGroup)?.removeView(hintView)
        }
        activeHintView = null
    }

    private fun showLongPressDescription(anchor: View, stringResName: String, fallback: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastHintShownAt < 400L) return
        lastHintShownAt = now

        val text = getLocalizedHint(stringResName, fallback)
        val rootContent = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return

        dismissLongPressHint()
        vibrateFeedback()

        val night = isNightMode
        val bgColor = if (night) Color.parseColor("#E623272D") else Color.parseColor("#F2F5F7F9")
        val textColor = if (night) Color.WHITE else Color.parseColor("#1A1D21")
        val strokeColor = if (night) Color.parseColor("#4DFFFFFF") else Color.parseColor("#2E000000")

        val badge = TextView(this).apply {
            this.text = text
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setPadding(toPx(20), toPx(12), toPx(20), toPx(12))
            elevation = toPx(16).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = toPx(24).toFloat()
                setColor(bgColor)
                setStroke(toPx(1), strokeColor)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                val anchorLoc = IntArray(2)
                val rootLoc = IntArray(2)
                anchor.getLocationInWindow(anchorLoc)
                rootContent.getLocationInWindow(rootLoc)
                val relativeY = anchorLoc[1] - rootLoc[1]
                topMargin = if (relativeY > toPx(110)) {
                    (relativeY - toPx(56)).coerceAtLeast(toPx(24))
                } else {
                    (relativeY + anchor.height + toPx(12)).coerceAtMost((rootContent.height - toPx(80)).coerceAtLeast(toPx(24)))
                }
            }
        }

        rootContent.addView(badge)
        activeHintView = badge
        hintHandler.postDelayed(hideHintRunnable, 2200L)
    }

    private fun attachHintLongClick(view: View?, stringResName: String, fallback: String) {
        view?.setOnLongClickListener { v ->
            showLongPressDescription(v, stringResName, fallback)
            true
        }
    }

    private fun setupLongPressDescriptions() {
        attachHintLongClick(btnRemoteEstop, "remote_hint_estop", "Not-Stopp am Drucker auslösen")
        attachHintLongClick(etTargetTvIp, "remote_hint_tv_ip", "IP-Adresse des Android-TV-Geräts")
        attachHintLongClick(btnSearchTv, "remote_hint_search_tv", "TV suchen oder neu verbinden")
        attachHintLongClick(btnLeaveRemote, "remote_hint_leave", "Fernbedienung schließen")
        attachHintLongClick(btnRemoteBackGlobal, "remote_hint_back", "Zurück-Befehl an TV senden")
        attachHintLongClick(btnToggleTouchMode, "remote_hint_touch_mode", "Zwischen D-Pad und Touchpad wechseln")

        attachHintLongClick(btnRemoteUp, "remote_hint_dpad_up", "Nach oben navigieren")
        attachHintLongClick(btnRemoteDown, "remote_hint_dpad_down", "Nach unten navigieren")
        attachHintLongClick(btnRemoteLeft, "remote_hint_dpad_left", "Nach links navigieren")
        attachHintLongClick(btnRemoteRight, "remote_hint_dpad_right", "Nach rechts navigieren")
        attachHintLongClick(btnRemoteOk, "remote_hint_dpad_ok", "Auswahl / OK bestätigen")

        attachHintLongClick(viewTouchPadField, "remote_hint_touch_pad", "Wischen für Mauszeiger, Tippen für Klick, 2 Finger zum Scrollen")
        attachHintLongClick(btnTouchBack, "remote_hint_touch_back", "Zurück-Befehl senden")
        attachHintLongClick(btnTouchReturnDpad, "remote_hint_touch_return_dpad", "Zurück zum D-Pad wechseln")
        attachHintLongClick(btnTouchOk, "remote_hint_touch_ok", "Mausklick an Cursor-Position ausführen")

        attachHintLongClick(btnRemoteStream, "remote_hint_stream", "Zwischen Livestream und Dashboard wechseln")
        attachHintLongClick(btnRemoteWebMenu, "remote_hint_web_menu", "Optionen-Menü am TV öffnen")
        attachHintLongClick(btnRemoteLayout, "remote_hint_layout", "Bild-in-Bild (PiP) umschalten")
        attachHintLongClick(btnRemoteBar, "remote_hint_bar", "OSD-Stil: Leiste aktivieren")
        attachHintLongClick(btnRemotePip, "remote_hint_pip", "On-Screen-Display (OSD) ein- oder ausblenden")
        attachHintLongClick(btnRemoteBox, "remote_hint_box", "OSD-Stil: Box aktivieren")

        attachHintLongClick(btnRemoteZoomIn, "remote_hint_zoom_in", "Ansicht verkleinern (Zoom -)")
        attachHintLongClick(btnRemoteZoomOut, "remote_hint_zoom_out", "Ansicht vergrößern (Zoom +)")
        attachHintLongClick(btnRemoteHome, "remote_hint_home", "Zurück / Startmenü")
        attachHintLongClick(btnRemoteCamera, "remote_hint_camera", "Kamera-Stream-Quelle wechseln")

        attachHintLongClick(btnRemoteSecUp, "remote_hint_sec_up", "OSD nach oben verschieben")
        attachHintLongClick(btnRemoteSecDown, "remote_hint_sec_down", "OSD nach unten verschieben")
        attachHintLongClick(btnRemoteSecLeft, "remote_hint_sec_left", "OSD nach links verschieben")
        attachHintLongClick(btnRemoteSecRight, "remote_hint_sec_right", "OSD nach rechts verschieben")
    }

    private fun initViews() {
        etTargetTvIp = findViewById(R.id.etTargetTvIp)
        tvRemoteStatus = findViewById(R.id.tvRemoteStatus)
        btnSearchTv = findViewById(R.id.btnSearchTv)
        tvFooterInfo = findViewById(R.id.tvFooterInfo)
        btnLeaveRemote = findViewById(R.id.btnLeaveRemote)

        btnRemoteUp = findViewById(R.id.btnRemoteUp)
        btnRemoteDown = findViewById(R.id.btnRemoteDown)
        btnRemoteLeft = findViewById(R.id.btnRemoteLeft)
        btnRemoteRight = findViewById(R.id.btnRemoteRight)
        btnRemoteOk = findViewById(R.id.btnRemoteOk)
        btnRemoteBackGlobal = findViewById(R.id.btnRemoteBackGlobal)

        btnRemoteHome = findViewById(R.id.btnRemoteHome)
        btnRemoteStream = findViewById(R.id.btnRemoteStream)
        btnRemoteWebMenu = findViewById(R.id.btnRemoteWebMenu)
        btnRemoteCamera = findViewById(R.id.btnRemoteCamera)
        btnRemoteEstop = findViewById(R.id.btnRemoteEstop)
        btnRemoteZoomIn = findViewById(R.id.btnRemoteZoomIn)
        btnRemoteZoomOut = findViewById(R.id.btnRemoteZoomOut)

        btnRemoteLayout = findViewById(R.id.btnRemoteLayout)
        btnRemotePip = findViewById(R.id.btnRemotePip)
        btnRemoteBar = findViewById(R.id.btnRemoteBar)
        btnRemoteBox = findViewById(R.id.btnRemoteBox)

        btnRemoteSecUp = findViewById(resources.getIdentifier("btnRemoteSecUp", "id", packageName))
        btnRemoteSecDown = findViewById(resources.getIdentifier("btnRemoteSecDown", "id", packageName))
        btnRemoteSecLeft = findViewById(resources.getIdentifier("btnRemoteSecLeft", "id", packageName))
        btnRemoteSecRight = findViewById(resources.getIdentifier("btnRemoteSecRight", "id", packageName))

        layoutDpadZone = findViewById(R.id.layoutDpadZone)
        layoutTouchZone = findViewById(R.id.layoutTouchZone)

        viewTouchPadField = findViewById(R.id.viewTouchPadField)
        btnTouchBack = findViewById(R.id.btnTouchBack)
        btnTouchReturnDpad = findViewById(R.id.btnTouchReturnDpad)
        btnTouchOk = findViewById(R.id.btnTouchOk)
        btnToggleTouchMode = findViewById(R.id.btnToggleTouchMode)

        etTargetTvIp.keyListener = DigitsKeyListener.getInstance("0123456789.")
        updatePrinterNameDisplay("offline")

        val strokeColor = if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A000000")

        val touchBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(ContextCompat.getColor(this@CompanionRemoteActivity, R.color.pill_normal_inactive))
            setStroke(3, strokeColor)
        }
        viewTouchPadField.background = touchBackground
        btnRemoteCamera.setIconResource(R.drawable.ic_desktop_landscape_add)
    }

    private fun checkCompanionModeLockState() {
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        val isCompanionEnabled = (activeRole != "disabled")

        if (isCompanionEnabled) {
            layoutDpadZone.alpha = 1.0f
            btnRemoteOk.isEnabled = true
            btnRemoteUp.isEnabled = true
            btnRemoteDown.isEnabled = true
            btnRemoteLeft.isEnabled = true
            btnRemoteRight.isEnabled = true
            btnRemoteBackGlobal.isEnabled = true
            btnToggleTouchMode.isEnabled = true
            btnToggleTouchMode.alpha = 1.0f
        } else {
            layoutDpadZone.alpha = 0.35f
            btnRemoteOk.isEnabled = false
            btnRemoteUp.isEnabled = false
            btnRemoteDown.isEnabled = false
            btnRemoteLeft.isEnabled = false
            btnRemoteRight.isEnabled = false
            btnRemoteBackGlobal.isEnabled = false

            setTouchMode(false)
            btnToggleTouchMode.isEnabled = false
            btnToggleTouchMode.alpha = 0.35f
            showCenteredPillToast(getString(R.string.companion_toggle_off))
        }
    }

    private fun applySyncedStateFromJson(json: JSONObject) {
        var changed = false
        if (json.has("isVideoMode")) {
            val v = json.optBoolean("isVideoMode", isVideoMode)
            if (v != isVideoMode) { isVideoMode = v; changed = true }
        }
        if (json.has("isOsdOn")) {
            val o = json.optBoolean("isOsdOn", isOsdOn)
            if (o != isOsdOn) { isOsdOn = o; changed = true }
        }
        if (json.has("isPipMode")) {
            val p = json.optBoolean("isPipMode", isPipMode)
            if (p != isPipMode) { isPipMode = p; changed = true }
        }
        if (json.has("osdStyle")) {
            val s = json.optString("osdStyle", currentOsdStyle)
            if (s != currentOsdStyle) { currentOsdStyle = s; changed = true }
        }

        if (changed) {
            prefs.edit()
                .putBoolean("remote_state_video", isVideoMode)
                .putBoolean("remote_state_osd", isOsdOn)
                .putBoolean("remote_state_pip", isPipMode)
                .putString("remote_state_style", currentOsdStyle)
                .apply()
            updateButtonColorState(btnRemoteStream, isVideoMode)
            updateButtonColorState(btnRemotePip, isOsdOn)
            updateButtonColorState(btnRemoteLayout, isPipMode)
            refreshOsdDependentUi()
        }
    }

    private fun startPersistentSession(ip: String) {
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled" || ip.isEmpty()) return

        sessionJob?.cancel()
        commandChannel.close()
        commandChannel = Channel(Channel.UNLIMITED)

        tvRemoteStatus.text = getString(R.string.remote_footer_connecting)
        tvRemoteStatus.setTypeface(null, Typeface.BOLD)
        tvRemoteStatus.setTextColor(Color.parseColor("#FFD54F"))
        updatePrinterNameDisplay("connecting")

        sessionJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                var socket: Socket? = null
                try {
                    socket = Socket().apply {
                        tcpNoDelay = true
                        keepAlive = true
                    }
                    socket.connect(InetSocketAddress(ip, 9999), 3000)
                    socket.soTimeout = 10000

                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                    withContext(Dispatchers.Main) {
                        isConnected = true
                        targetMasterIp = ip
                        prefs.edit().putString("last_master_tv_ip", ip).apply()
                        tvRemoteStatus.text = getString(R.string.remote_status_connected)
                        tvRemoteStatus.setTextColor(Color.parseColor("#4CAF50"))
                    }

                    commandChannel.trySend("REQUEST_SYNC_STATE")

                    coroutineScope {
                        val readerJob = launch {
                            while (isActive) {
                                val line = reader.readLine() ?: throw Exception("EOF")
                                val trimmed = line.trim()
                                if (trimmed.startsWith("{")) {
                                    try {
                                        val json = JSONObject(trimmed)
                                        val liveModel = if (json.has("model")) json.optString("model") else null
                                        val liveName = if (json.has("name")) json.optString("name") else null
                                        withContext(Dispatchers.Main) {
                                            if (!isFinishing && !isDestroyed) {
                                                applySyncedStateFromJson(json)
                                                updatePrinterNameDisplay("online", liveModel, liveName)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        val heartbeatJob = launch {
                            while (isActive) {
                                delay(3000)
                                commandChannel.trySend("REQUEST_SYNC_STATE")
                            }
                        }

                        try {
                            for (cmd in commandChannel) {
                                writer.write(cmd)
                                writer.newLine()
                                writer.flush()
                            }
                        } finally {
                            readerJob.cancel()
                            heartbeatJob.cancel()
                        }
                    }
                } catch (e: Exception) {
                    Log.d("KlippShell", "Companion Session unterbrochen ($ip): ${e.message}")
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            isConnected = false
                            isWebMenuOpen = false
                            updateButtonColorState(btnRemoteWebMenu, false)
                            tvRemoteStatus.text = getString(R.string.remote_status_disconnected)
                            tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
                            updatePrinterNameDisplay("offline")
                        }
                    }
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }

                delay(2500)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startNsdDiscovery(onFound: (String) -> Unit) {
        stopNsdDiscovery()
        try {
            val manager = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType.contains("_klippshell._tcp")) {
                        manager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val hostIp = info.host?.hostAddress ?: return
                                if (hostIp.isNotEmpty() && !hostIp.startsWith("127.")) {
                                    stopNsdDiscovery()
                                    onFound(hostIp)
                                }
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { stopNsdDiscovery() }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stopNsdDiscovery() }
            }
            nsdDiscoveryListener = listener
            manager.discoverServices("_klippshell._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.d("KlippShell", "NSD Discovery Fehler: ${e.message}")
        }
    }

    private fun stopNsdDiscovery() {
        try {
            nsdDiscoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        nsdDiscoveryListener = null
    }

    private fun discoverAndConnectTvAuto() {
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled") {
            isConnected = false
            tvRemoteStatus.text = getString(R.string.remote_status_disconnected)
            tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
            updatePrinterNameDisplay("offline")
            return
        }

        discoveryJob?.cancel()
        tvRemoteStatus.text = getString(R.string.remote_footer_connecting)
        tvRemoteStatus.setTypeface(null, Typeface.BOLD)
        tvRemoteStatus.setTextColor(Color.parseColor("#FFD54F"))
        updatePrinterNameDisplay("connecting")

        var resolved = false
        val onTvDiscovered: (String) -> Unit = { foundIp ->
            if (!resolved) {
                resolved = true
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        targetMasterIp = foundIp
                        etTargetTvIp.setText(foundIp)
                        prefs.edit().putString("last_master_tv_ip", foundIp).apply()
                        startPersistentSession(foundIp)
                    }
                }
            }
        }

        startNsdDiscovery(onTvDiscovered)

        discoveryJob = lifecycleScope.launch(Dispatchers.IO) {
            var datagramSocket: DatagramSocket? = null
            try {
                datagramSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 2500
                    broadcast = true
                    bind(InetSocketAddress(0))
                }
                val requestData = "DISCOVER_KLIPPSHELL_MASTER".toByteArray(Charsets.UTF_8)
                val broadcastPacket = DatagramPacket(requestData, requestData.size, InetAddress.getByName("255.255.255.255"), 9998)
                datagramSocket.send(broadcastPacket)

                val receiveBuffer = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                datagramSocket.receive(receivePacket)

                val senderIp = receivePacket.address?.hostAddress?.trim() ?: ""
                if (senderIp.isNotEmpty() && senderIp != "127.0.0.1") {
                    stopNsdDiscovery()
                    onTvDiscovered(senderIp)
                    return@launch
                }
            } catch (e: Exception) {
                Log.d("KlippShell", "UDP Discovery Timeout: ${e.message}")
            } finally {
                try { datagramSocket?.close() } catch (_: Exception) {}
            }

            if (!resolved) {
                delay(1500)
                stopNsdDiscovery()
                if (!resolved) {
                    withContext(Dispatchers.Main) {
                        if (targetMasterIp.isNotEmpty()) {
                            startPersistentSession(targetMasterIp)
                        } else {
                            tvRemoteStatus.text = getString(R.string.remote_status_disconnected)
                            tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
                            updatePrinterNameDisplay("offline")
                            showCenteredPillToast(getString(R.string.toast_no_connection))
                        }
                    }
                }
            }
        }
    }

    private fun updatePrinterNameDisplay(state: String, fetchedModel: String? = null, fetchedName: String? = null) {
        var localModelName = getString(R.string.printer_model_default)
        var localPrinterName = ""

        try {
            val printersJson = prefs.getString("printers_list", "[]") ?: "[]"
            val arr = JSONArray(printersJson)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                localModelName = obj.optString("model", localModelName)
                localPrinterName = obj.optString("name", "")
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Laden des Offline-Druckers", e)
        }

        val localDisplay = if (localPrinterName.isNotEmpty()) "$localPrinterName ($localModelName)" else localModelName

        when (state) {
            "connecting" -> tvFooterInfo.text = getString(R.string.remote_footer_connecting)
            "online" -> {
                val model = fetchedModel ?: localModelName
                val name = fetchedName ?: localPrinterName
                val liveDisplay = if (name.isNotEmpty()) "$name ($model)" else model
                tvFooterInfo.text = getString(R.string.remote_footer_online, liveDisplay)
            }
            else -> tvFooterInfo.text = getString(R.string.remote_footer_offline, localDisplay)
        }
    }

    private fun applyFloatingBenchyBackground() {
        try {
            val decorView = window.decorView as ViewGroup
            val benchyView = ImageView(this).apply {
                setImageResource(R.drawable.benchy_boat)
                alpha = 0.06f
                background = null
            }
            decorView.addView(benchyView)

            lifecycleScope.launch(Dispatchers.Default) {
                while (decorView.width == 0 || decorView.height == 0) delay(50)
                val boatSize = (decorView.width * 0.45f).toInt()
                withContext(Dispatchers.Main) { benchyView.layoutParams = FrameLayout.LayoutParams(boatSize, boatSize) }

                var posX = (decorView.width - boatSize) / 2f
                var posY = decorView.height * 0.35f
                var speedX = 0.12f
                var speedY = 0.09f

                while (isActive) {
                    val currentW = decorView.width
                    val currentH = decorView.height
                    posX += speedX
                    posY += speedY

                    var flipX: Float? = null
                    if (posX <= 0f) { speedX = abs(speedX); posX = 0f; flipX = 1f }
                    else if (posX + boatSize >= currentW) { speedX = -abs(speedX); posX = (currentW - boatSize).toFloat().coerceAtLeast(0f); flipX = -1f }

                    if (posY <= 0f) { speedY = abs(speedY); posY = 0f }
                    else if (posY + boatSize >= currentH) { speedY = -abs(speedY); posY = (currentH - boatSize).toFloat().coerceAtLeast(0f) }

                    val renderX = posX
                    val renderY = posY
                    withContext(Dispatchers.Main) {
                        flipX?.let { benchyView.scaleX = it }
                        benchyView.x = renderX
                        benchyView.y = renderY
                    }
                    delay(32)
                }
            }
        } catch (e: Exception) { Log.e("KlippShell", "Benchy Background Transparenz Fehler", e) }
    }

    private fun applyDynamicVignetteBorder() {
        try {
            val rootContent = findViewById<ViewGroup>(android.R.id.content)
            if (rootContent != null) {
                val vignetteView = View(this).apply { isClickable = false; isFocusable = false; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
                vignetteView.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    val w = right - left
                    val h = bottom - top
                    if (w > 0 && h > 0) {
                        val radius = sqrt((w * w + h * h).toDouble()).toFloat() * 0.72f
                        v.background = GradientDrawable().apply {
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            setColors(intArrayOf(Color.TRANSPARENT, Color.parseColor("#1F000000"), Color.parseColor("#D9000000")))
                            gradientRadius = radius
                            setGradientCenter(0.5f, 0.5f)
                        }
                    }
                }
                rootContent.addView(vignetteView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } catch (e: Exception) { Log.e("KlippShell", "Vignette fehlgeschlagen", e) }
    }

    private fun setTouchMode(enabled: Boolean) {
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled" && enabled) return
        isTouchModeActive = enabled
        if (enabled) {
            layoutDpadZone.visibility = View.GONE
            btnRemoteBackGlobal.visibility = View.GONE
            layoutTouchZone.visibility = View.VISIBLE
            btnToggleTouchMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btnToggleTouchMode.setIconResource(R.drawable.ic_trackpad_mouse)
        } else {
            layoutDpadZone.visibility = View.VISIBLE
            btnRemoteBackGlobal.visibility = View.VISIBLE
            layoutTouchZone.visibility = View.GONE
            btnToggleTouchMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            btnToggleTouchMode.setIconResource(R.drawable.ic_trackpad_mouse)
            if (isConnected) sendCommandToTv("HIDE_CURSOR", vibrate = false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        btnSearchTv.setOnClickListener {
            vibrateFeedback()
            val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
            if (activeRole == "disabled") {
                showCenteredPillToast(getString(R.string.companion_toggle_off))
                return@setOnClickListener
            }
            val ip = etTargetTvIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                startPersistentSession(ip)
            } else {
                discoverAndConnectTvAuto()
            }
        }

        btnToggleTouchMode.setOnClickListener { vibrateFeedback(); setTouchMode(!isTouchModeActive) }
        btnTouchReturnDpad.setOnClickListener { vibrateFeedback(); setTouchMode(false) }

        btnTouchBack.setOnClickListener {
            isWebMenuOpen = false
            updateButtonColorState(btnRemoteWebMenu, false)
            sendCommandToTv("BACK")
        }
        btnRemoteBackGlobal.setOnClickListener {
            isWebMenuOpen = false
            updateButtonColorState(btnRemoteWebMenu, false)
            sendCommandToTv("BACK")
        }

        btnTouchOk.setOnClickListener { sendCommandToTv("MOUSE_CLICK") }
        btnLeaveRemote.setOnClickListener { vibrateFeedback(); finish() }
        btnRemoteUp.setOnClickListener { sendCommandToTv("DPAD_UP") }
        btnRemoteDown.setOnClickListener { sendCommandToTv("DPAD_DOWN") }
        btnRemoteLeft.setOnClickListener { sendCommandToTv("DPAD_LEFT") }
        btnRemoteRight.setOnClickListener { sendCommandToTv("DPAD_RIGHT") }
        btnRemoteOk.setOnClickListener { sendCommandToTv("DPAD_OK") }
        btnRemoteHome.setOnClickListener {
            isWebMenuOpen = false
            updateButtonColorState(btnRemoteWebMenu, false)
            sendCommandToTv("BACK")
        }

        btnRemoteZoomIn.setOnClickListener { sendCommandToTv("ZOOM_OUT") }
        btnRemoteZoomOut.setOnClickListener { sendCommandToTv("ZOOM_IN") }

        btnRemoteWebMenu.setOnClickListener {
            vibrateFeedback()
            if (!isWebMenuOpen) {
                isWebMenuOpen = true
                updateButtonColorState(btnRemoteWebMenu, true)
                sendCommandToTv("WEB_MENU_OPEN")
            } else {
                isWebMenuOpen = false
                updateButtonColorState(btnRemoteWebMenu, false)
                sendCommandToTv("BACK")
            }
        }

        viewTouchPadField.setOnTouchListener { _, event ->
            val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
            if (activeRole == "disabled") return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
                    touchDownTime = SystemClock.uptimeMillis()
                    pendingDeltaX = 0f
                    pendingDeltaY = 0f
                    lastMoveSentTime = touchDownTime
                    wasTwoFingerGesture = false
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount >= 2) {
                        wasTwoFingerGesture = true
                        lastTouchY = (event.getY(0) + event.getY(1)) / 2f
                        pendingDeltaY = 0f
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val now = SystemClock.uptimeMillis()
                    if (event.pointerCount >= 2) {
                        wasTwoFingerGesture = true
                        val avgY = (event.getY(0) + event.getY(1)) / 2f
                        pendingDeltaY += (avgY - lastTouchY)
                        lastTouchY = avgY
                        if (now - lastMoveSentTime >= 35L && abs(pendingDeltaY) >= 4f) {
                            val scrollDy = (-pendingDeltaY * 2.5f).toInt()
                            if (scrollDy != 0) {
                                sendCommandToTv("SCROLL;$scrollDy", vibrate = false)
                                pendingDeltaY = 0f
                                lastMoveSentTime = now
                            }
                        }
                    } else if (!wasTwoFingerGesture) {
                        pendingDeltaX += (event.x - lastTouchX)
                        pendingDeltaY += (event.y - lastTouchY)
                        lastTouchX = event.x
                        lastTouchY = event.y

                        if (now - lastMoveSentTime >= 25L && (abs(pendingDeltaX) >= 1.2f || abs(pendingDeltaY) >= 1.2f)) {
                            val dx = pendingDeltaX.toInt()
                            val dy = pendingDeltaY.toInt()
                            if (dx != 0 || dy != 0) {
                                sendCommandToTv("MOUSE_MOVE;$dx;$dy", vibrate = false)
                                pendingDeltaX = 0f
                                pendingDeltaY = 0f
                                lastMoveSentTime = now
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!wasTwoFingerGesture) {
                        val elapsed = SystemClock.uptimeMillis() - touchDownTime
                        val totalDist = hypot((event.x - touchDownX).toDouble(), (event.y - touchDownY).toDouble())

                        if (elapsed < 250L && totalDist < 20.0) {
                            sendCommandToTv("MOUSE_CLICK", vibrate = true)
                        } else if (abs(pendingDeltaX) >= 1f || abs(pendingDeltaY) >= 1f) {
                            val dx = pendingDeltaX.toInt()
                            val dy = pendingDeltaY.toInt()
                            if (dx != 0 || dy != 0) {
                                sendCommandToTv("MOUSE_MOVE;$dx;$dy", vibrate = false)
                            }
                        }
                    }
                    pendingDeltaX = 0f
                    pendingDeltaY = 0f
                    wasTwoFingerGesture = false
                    true
                }
                else -> false
            }
        }

        btnRemoteEstop.setOnClickListener {
            vibrateFeedback()
            sendCommandToTv("ESTOP")
        }

        etTargetTvIp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { prefs.edit().putString("last_master_tv_ip", s.toString().trim()).apply() }
        })
    }

    private fun setupSpecialToggles() {
        updateButtonColorState(btnRemoteStream, isVideoMode)
        updateButtonColorState(btnRemotePip, isOsdOn)
        updateButtonColorState(btnRemoteLayout, isPipMode)
        updateButtonColorState(btnRemoteWebMenu, isWebMenuOpen)

        btnRemoteStream.setOnClickListener { vibrateFeedback(); isVideoMode = !isVideoMode; prefs.edit().putBoolean("remote_state_video", isVideoMode).apply(); updateButtonColorState(btnRemoteStream, isVideoMode); sendCommandToTv("TOGGLE_VIDEO") }
        btnRemotePip.setOnClickListener { vibrateFeedback(); isOsdOn = !isOsdOn; prefs.edit().putBoolean("remote_state_osd", isOsdOn).apply(); updateButtonColorState(btnRemotePip, isOsdOn); refreshOsdDependentUi(); sendCommandToTv("TOGGLE_OSD") }
        btnRemoteLayout.setOnClickListener { vibrateFeedback(); isPipMode = !isPipMode; prefs.edit().putBoolean("remote_state_pip", isPipMode).apply(); updateButtonColorState(btnRemoteLayout, isPipMode); sendCommandToTv("PIP_TOGGLE") }
        btnRemoteBox.setOnClickListener { vibrateFeedback(); currentOsdStyle = "box"; prefs.edit().putString("remote_state_style", "box").apply(); refreshOsdDependentUi(); sendCommandToTv("STYLE_BOX") }
        btnRemoteBar.setOnClickListener { vibrateFeedback(); currentOsdStyle = "banner"; prefs.edit().putString("remote_state_style", "banner").apply(); refreshOsdDependentUi(); sendCommandToTv("STYLE_BANNER") }

        mapOf("btnRemoteSecUp" to "SEC_UP", "btnRemoteSecDown" to "SEC_DOWN", "btnRemoteSecLeft" to "SEC_LEFT", "btnRemoteSecRight" to "SEC_RIGHT").forEach { (resName, token) ->
            val resId = resources.getIdentifier(resName, "id", packageName)
            if (resId != 0) findViewById<MaterialButton>(resId)?.setOnClickListener { vibrateFeedback(); if (isOsdOn) sendCommandToTv(token) }
        }
        btnRemoteCamera.setOnClickListener { sendCommandToTv("CAMERA_CYCLE") }
    }

    private fun refreshOsdDependentUi() {
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled") return

        val smallDpad = arrayOf(btnRemoteSecUp, btnRemoteSecDown, btnRemoteSecLeft, btnRemoteSecRight)
        if (isOsdOn) {
            smallDpad.forEach { btn -> btn?.isEnabled = true; btn?.alpha = 1.0f }
            btnRemoteBox.isEnabled = true; btnRemoteBar.isEnabled = true; btnRemoteBox.alpha = 1.0f; btnRemoteBar.alpha = 1.0f
            updateButtonColorState(btnRemoteBox, currentOsdStyle == "box"); updateButtonStateGrey(btnRemoteBar, currentOsdStyle == "banner")
        } else {
            smallDpad.forEach { btn -> btn?.isEnabled = false; btn?.alpha = 0.35f }
            btnRemoteBox.isEnabled = false; btnRemoteBar.isEnabled = false; btnRemoteBox.alpha = 0.35f; btnRemoteBar.alpha = 0.35f
            val darkGrey = ColorStateList.valueOf(Color.parseColor("#424242"))
            btnRemoteBox.backgroundTintList = darkGrey; btnRemoteBar.backgroundTintList = darkGrey
            btnRemoteBox.setTextColor(Color.parseColor("#80FFFFFF")); btnRemoteBar.setTextColor(Color.parseColor("#80FFFFFF"))
        }
    }

    private fun updateButtonColorState(button: MaterialButton, isActive: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isActive) "#4CAF50" else "#2196F3"))
        button.setTextColor(Color.WHITE)
    }

    private fun updateButtonStateGrey(button: MaterialButton, isActive: Boolean) {
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isActive) "#4CAF50" else "#E0E0E0"))
        button.setTextColor(if (isActive) Color.WHITE else Color.BLACK)
    }

    private fun applyUnifiedButtonShapesAndFocus() {
        val borderHighlight = targetBorderColor
        val combinedButtons = mutableListOf(
            btnSearchTv, btnLeaveRemote, btnRemoteUp, btnRemoteDown, btnRemoteLeft, btnRemoteRight,
            btnRemoteOk, btnRemoteHome, btnRemoteStream, btnRemoteWebMenu, btnRemoteCamera, btnRemoteEstop, btnRemoteZoomIn,
            btnRemoteZoomOut, btnRemoteLayout, btnRemotePip, btnRemoteBar, btnRemoteBox,
            btnTouchBack, btnTouchReturnDpad, btnTouchOk, btnToggleTouchMode, btnRemoteBackGlobal
        )
        arrayOf(btnRemoteSecUp, btnRemoteSecDown, btnRemoteSecLeft, btnRemoteSecRight).forEach { btn -> btn?.let { combinedButtons.add(it) } }

        combinedButtons.forEach { btn ->
            if (btn != btnTouchBack && btn != btnTouchReturnDpad && btn != btnTouchOk &&
                btn != btnRemoteUp && btn != btnRemoteDown && btn != btnRemoteLeft && btn != btnRemoteRight &&
                btn != btnRemoteSecUp && btn != btnRemoteSecDown && btn != btnRemoteSecLeft && btn != btnRemoteSecRight) {
                btn?.shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            }
            if (btn == btnRemoteEstop) { btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935")); btn.setTextColor(Color.WHITE) }
            val oldListener = btn?.onFocusChangeListener
            btn?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus -> oldListener?.onFocusChange(v, hasFocus); if (v.isEnabled) { v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(120).start(); if (v is MaterialButton) { v.strokeWidth = if (hasFocus) 6 else 0; v.strokeColor = if (hasFocus) ColorStateList.valueOf(borderHighlight) else null } } }
        }
    }

    private fun sendCommandToTv(command: String, vibrate: Boolean = true) {
        if (vibrate) vibrateFeedback()
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled") return

        if (command != "MOUSE_MOVE" && !command.startsWith("MOUSE_MOVE;") && !command.startsWith("SCROLL;")) {
            val now = SystemClock.uptimeMillis()
            val lastSent = lastCommandSentAt[command] ?: 0L
            if (now - lastSent < 280L) return
            lastCommandSentAt[command] = now
        }

        if (!isConnected || targetMasterIp.isEmpty()) {
            if (vibrate) showCenteredPillToast(getString(R.string.toast_no_connection))
            return
        }
        commandChannel.trySend(command)
    }

    private fun vibrateFeedback() {
        val view = findViewById<View>(android.R.id.content)
        view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    private fun showCenteredPillToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.CENTER, 0, 0)
        toast.show()
    }

    override fun onDestroy() {
        dismissLongPressHint()
        stopNsdDiscovery()
        discoveryJob?.cancel()
        sessionJob?.cancel()
        commandChannel.close()
        super.onDestroy()
    }
}