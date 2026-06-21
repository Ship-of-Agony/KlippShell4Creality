package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
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

    private lateinit var btnRemoteHome: MaterialButton
    private lateinit var btnRemoteStream: MaterialButton
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
    private var connectionJob: Job? = null
    private var isConnected = false

    private var isVideoMode = false
    private var isOsdOn = false
    private var isPipMode = false
    private var currentOsdStyle = "box"

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isTouchModeActive = false

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
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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
        applyUnifiedButtonShapesAndFocus()

        refreshOsdDependentUi()
        applyFloatingBenchyBackground()
        applyDynamicVignetteBorder()

        checkCompanionModeLockState()

        val deviceRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (targetMasterIp.isEmpty() || deviceRole == "auto") {
            discoverAndConnectTvAuto()
        } else {
            etTargetTvIp.setText(targetMasterIp)
            triggerConnectionCheck(targetMasterIp)
        }
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

        btnRemoteHome = findViewById(R.id.btnRemoteHome)
        btnRemoteStream = findViewById(R.id.btnRemoteStream)
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

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val strokeColor = if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A000000")

        val touchBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 48f
            setColor(ContextCompat.getColor(this@CompanionRemoteActivity, R.color.pill_normal_inactive))
            setStroke(3, strokeColor)
        }
        viewTouchPadField.background = touchBackground
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
            btnToggleTouchMode.isEnabled = true
            btnToggleTouchMode.alpha = 1.0f
        } else {
            layoutDpadZone.alpha = 0.35f
            btnRemoteOk.isEnabled = false
            btnRemoteUp.isEnabled = false
            btnRemoteDown.isEnabled = false
            btnRemoteLeft.isEnabled = false
            btnRemoteRight.isEnabled = false

            setTouchMode(false)
            btnToggleTouchMode.isEnabled = false
            btnToggleTouchMode.alpha = 0.35f

            showCenteredPillToast(getString(R.string.autostart_disabled))
        }
    }

    private fun updatePrinterNameDisplay(state: String, fetchedModel: String? = null, fetchedName: String? = null) {
        var localModelName = "K2"
        var localPrinterName = ""

        try {
            val printersJson = prefs.getString("printers_list", "[]") ?: "[]"
            val arr = JSONArray(printersJson)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                localModelName = obj.optString("model", "K2")
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
                val liveDisplay = if (!name.isNullOrEmpty()) "$name ($model)" else model
                tvFooterInfo.text = getString(R.string.remote_footer_online, liveDisplay)
            }
            else -> tvFooterInfo.text = getString(R.string.remote_footer_offline, localDisplay)
        }
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

        connectionJob?.cancel()
        tvRemoteStatus.text = getString(R.string.toast_loading_dashboard)
        tvRemoteStatus.setTypeface(null, Typeface.BOLD)
        tvRemoteStatus.setTextColor(Color.parseColor("#FFD54F"))
        updatePrinterNameDisplay("connecting")

        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            var datagramSocket: DatagramSocket? = null
            try {
                datagramSocket = DatagramSocket().apply { soTimeout = 2500; broadcast = true }
                val requestData = "DISCOVER_KLIPPSHELL_MASTER".toByteArray(Charsets.UTF_8)
                val broadcastPacket = DatagramPacket(requestData, requestData.size, InetAddress.getByName("255.255.255.255"), 9998)
                datagramSocket.send(broadcastPacket)

                val receiveBuffer = ByteArray(1024)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                datagramSocket.receive(receivePacket)

                val responseStr = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8).trim()
                if (responseStr.isNotEmpty()) {
                    val json = JSONObject(responseStr)
                    val discoveredIp = if (json.has("ip")) json.getString("ip") else receivePacket.address.hostAddress
                    val liveModel = if (json.has("model")) json.getString("model") else null
                    val liveName = if (json.has("name")) json.getString("name") else null

                    withContext(Dispatchers.Main) {
                        isConnected = true
                        targetMasterIp = discoveredIp
                        etTargetTvIp.setText(discoveredIp)
                        prefs.edit().putString("last_master_tv_ip", discoveredIp).apply()

                        tvRemoteStatus.text = getString(R.string.remote_status_connected)
                        tvRemoteStatus.setTextColor(Color.parseColor("#4CAF50"))
                        updatePrinterNameDisplay("online", liveModel, liveName)
                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.d("KlippShell", "Auto Discovery passiv abgelaufen")
            } finally {
                try { datagramSocket?.close() } catch (_: Exception) {}
            }

            if (targetMasterIp.isNotEmpty()) {
                withContext(Dispatchers.Main) { triggerConnectionCheck(targetMasterIp) }
            } else {
                withContext(Dispatchers.Main) {
                    isConnected = false
                    tvRemoteStatus.text = getString(R.string.toast_no_connection)
                    tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
                    updatePrinterNameDisplay("offline")
                }
            }
        }
    }

    private fun applyFloatingBenchyBackground() {
        try {
            val rootContent = findViewById<ViewGroup>(android.R.id.content)
            val inflatedLayout = rootContent?.getChildAt(0) as? ViewGroup

            if (rootContent != null && inflatedLayout != null) {
                val benchyView = ImageView(this).apply { setImageResource(R.drawable.benchy_boat); alpha = 0.06f }
                val originalXmlBg = inflatedLayout.background
                if (originalXmlBg != null) { rootContent.background = originalXmlBg; inflatedLayout.background = null }
                rootContent.addView(benchyView, 0)

                lifecycleScope.launch(Dispatchers.Main) {
                    val decorView = window.decorView
                    while (decorView.width == 0 || decorView.height == 0) delay(30)
                    val containerW = decorView.width
                    val containerH = decorView.height
                    val boatSize = (containerW * 0.45f).toInt()
                    benchyView.layoutParams = FrameLayout.LayoutParams(boatSize, boatSize)

                    var posX = (containerW - boatSize) / 2f
                    var posY = containerH * 0.35f
                    var speedX = 0.06f
                    var speedY = 0.045f

                    while (isActive) {
                        val currentW = decorView.width
                        val currentH = decorView.height
                        posX += speedX
                        posY += speedY

                        if (posX <= 0f) { speedX = abs(speedX); posX = 0f; benchyView.scaleX = 1f }
                        else if (posX + boatSize >= currentW) { speedX = -abs(speedX); posX = (containerW - boatSize).toFloat(); benchyView.scaleX = -1f }

                        if (posY <= 0f) { speedY = abs(speedY); posY = 0f }
                        else if (posY + boatSize >= currentH) { speedY = -abs(speedY); posY = (containerH - boatSize).toFloat() }

                        benchyView.x = posX
                        benchyView.y = posY
                        delay(16)
                    }
                }
            }
        } catch (e: Exception) { Log.e("KlippShell", "Benchy Background fehlgeschlagen", e) }
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
            layoutTouchZone.visibility = View.VISIBLE
            btnToggleTouchMode.visibility = View.GONE
        } else {
            layoutDpadZone.visibility = View.VISIBLE
            layoutTouchZone.visibility = View.GONE
            btnToggleTouchMode.visibility = View.VISIBLE
            btnToggleTouchMode.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            btnToggleTouchMode.setIconResource(R.drawable.ic_trackpad_mouse)
        }
    }

    private fun setupListeners() {
        btnSearchTv.setOnClickListener {
            val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
            if (activeRole == "disabled") {
                showCenteredPillToast(getString(R.string.autostart_disabled))
                return@setOnClickListener
            }
            val ip = etTargetTvIp.text.toString().trim()
            if (ip.isNotEmpty()) triggerConnectionCheck(ip) else discoverAndConnectTvAuto()
        }

        btnToggleTouchMode.setOnClickListener { vibrateFeedback(); setTouchMode(!isTouchModeActive) }
        btnTouchReturnDpad.setOnClickListener { vibrateFeedback(); setTouchMode(false) }
        btnTouchBack.setOnClickListener { sendCommandToTv("BACK") }
        btnTouchOk.setOnClickListener { sendCommandToTv("DPAD_OK") }

        btnLeaveRemote.setOnClickListener { vibrateFeedback(); finish() }
        btnRemoteUp.setOnClickListener { sendCommandToTv("DPAD_UP") }
        btnRemoteDown.setOnClickListener { sendCommandToTv("DPAD_DOWN") }
        btnRemoteLeft.setOnClickListener { sendCommandToTv("DPAD_LEFT") }
        btnRemoteRight.setOnClickListener { sendCommandToTv("DPAD_RIGHT") }
        btnRemoteOk.setOnClickListener { sendCommandToTv("DPAD_OK") }
        btnRemoteHome.setOnClickListener { sendCommandToTv("BACK") }
        btnRemoteZoomIn.setOnClickListener { sendCommandToTv("ZOOM_IN") }
        btnRemoteZoomOut.setOnClickListener { sendCommandToTv("ZOOM_OUT") }

        viewTouchPadField.setOnTouchListener { _, event ->
            val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
            if (activeRole == "disabled") return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTouchX = event.x; lastTouchY = event.y; vibrateFeedback(); true }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    if (abs(deltaX) > 2 || abs(deltaY) > 2) {
                        sendCommandToTv("MOUSE_MOVE;${deltaX.toInt()};${deltaY.toInt()}")
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                    true
                }
                else -> false
            }
        }

        btnRemoteEstop.setOnClickListener {
            vibrateFeedback()
            val dialogContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; val pad = (24 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad); background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 48f; setColor(Color.parseColor("#E53935")) }; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) }
            TextView(this).apply { text = getString(R.string.menu_emergency_stop); textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD) }.let { dialogContainer.addView(it) }
            TextView(this).apply { text = getString(R.string.dialog_stop_title); textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }.let { dialogContainer.addView(it) }
            val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
            val pVert = (12 * resources.displayMetrics.density).toInt()

            val btnCancel = MaterialButton(this).apply { text = getString(R.string.cancel); isAllCaps = false; textSize = 15f; isFocusable = true; setTextColor(Color.parseColor("#E53935")); backgroundTintList = ColorStateList.valueOf(Color.WHITE); shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build(); setPadding(0, pVert, 0, pVert) }
            val btnConfirm = MaterialButton(this).apply { text = getString(R.string.dialog_stop_confirm); isAllCaps = false; textSize = 15f; isFocusable = true; setTextColor(Color.WHITE); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B71C1C")); shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build(); setPadding(0, pVert, 0, pVert) }

            buttonRow.addView(btnCancel)
            buttonRow.addView(btnConfirm)
            dialogContainer.addView(buttonRow)
            val dialog = AlertDialog.Builder(this).setView(dialogContainer).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val borderHighlight = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#424242")
            arrayOf(btnCancel, btnConfirm).forEach { btn -> btn.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus -> v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(100).start(); if (v is MaterialButton) { v.strokeWidth = if (hasFocus) 6 else 0; v.strokeColor = if (hasFocus) ColorStateList.valueOf(borderHighlight) else null } } }
            btnCancel.setOnClickListener { dialog.dismiss() }
            btnConfirm.setOnClickListener { dialog.dismiss(); sendCommandToTv("ESTOP") }
            dialog.show()
            btnCancel.requestFocus()
        }

        etTargetTvIp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { prefs.edit().putString("last_master_tv_ip", s.toString().trim()).apply() }
        })
    }

    private fun setupSpecialToggles() {
        updateButtonColorState(btnRemoteStream, isVideoMode)
        btnRemoteStream.setOnClickListener { vibrateFeedback(); isVideoMode = !isVideoMode; prefs.edit().putBoolean("remote_state_video", isVideoMode).apply(); updateButtonColorState(btnRemoteStream, isVideoMode); sendCommandToTv("TOGGLE_VIDEO") }
        updateButtonColorState(btnRemotePip, isOsdOn)
        btnRemotePip.setOnClickListener { vibrateFeedback(); isOsdOn = !isOsdOn; prefs.edit().putBoolean("remote_state_osd", isOsdOn).apply(); updateButtonColorState(btnRemotePip, isOsdOn); refreshOsdDependentUi(); sendCommandToTv("TOGGLE_OSD") }
        updateButtonColorState(btnRemoteLayout, isPipMode)
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
            updateButtonColorState(btnRemoteBox, currentOsdStyle == "box"); updateButtonColorState(btnRemoteBar, currentOsdStyle == "banner")
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

    private fun applyUnifiedButtonShapesAndFocus() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val borderHighlight = if (isNight) Color.parseColor("#4CAF50") else Color.parseColor("#424242")
        val combinedButtons = mutableListOf(
            btnSearchTv, btnLeaveRemote, btnRemoteUp, btnRemoteDown, btnRemoteLeft, btnRemoteRight,
            btnRemoteOk, btnRemoteHome, btnRemoteStream, btnRemoteCamera, btnRemoteEstop, btnRemoteZoomIn,
            btnRemoteZoomOut, btnRemoteLayout, btnRemotePip, btnRemoteBar, btnRemoteBox,
            btnTouchBack, btnTouchReturnDpad, btnTouchOk, btnToggleTouchMode
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

    private fun triggerConnectionCheck(ip: String) {
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled") return

        connectionJob?.cancel()
        tvRemoteStatus.text = getString(R.string.toast_loading_dashboard)
        tvRemoteStatus.setTypeface(null, Typeface.BOLD)
        tvRemoteStatus.setTextColor(Color.parseColor("#FFD54F"))
        updatePrinterNameDisplay("connecting")

        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, 9999), 3000)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"))
                writer.write("REQUEST_PRINTER_INFO\n")
                writer.flush()
                val borderReader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
                val response = borderReader.readLine()
                var liveModel: String? = null
                var liveName: String? = null
                if (!response.isNullOrEmpty()) {
                    try {
                        val json = JSONObject(response)
                        liveModel = if (json.has("model")) json.getString("model") else null
                        liveName = if (json.has("name")) json.getString("name") else null
                    } catch (e: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    isConnected = true
                    targetMasterIp = ip
                    tvRemoteStatus.text = getString(R.string.remote_status_connected)
                    tvRemoteStatus.setTextColor(Color.parseColor("#4CAF50"))
                    updatePrinterNameDisplay("online", liveModel, liveName)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnected = false
                    tvRemoteStatus.text = getString(R.string.toast_no_connection)
                    tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
                    updatePrinterNameDisplay("offline")
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun sendCommandToTv(command: String) {
        vibrateFeedback()
        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        if (activeRole == "disabled") return

        if (!isConnected || targetMasterIp.isEmpty()) { showCenteredPillToast(getString(R.string.toast_no_connection)); return }
        lifecycleScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(targetMasterIp, 9999), 1500)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"))
                writer.write(command + "\n")
                writer.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { isConnected = false; tvRemoteStatus.text = getString(R.string.remote_status_disconnected); tvRemoteStatus.setTextColor(Color.parseColor("#E53935")); updatePrinterNameDisplay("offline") }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
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

    override fun onDestroy() { connectionJob?.cancel(); super.onDestroy() }
}