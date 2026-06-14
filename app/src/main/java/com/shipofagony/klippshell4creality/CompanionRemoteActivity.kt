package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
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
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

    // Referenzen für das kleine OSD-D-Pad zur dynamischen Kaskaden-Sperrung
    private var btnRemoteSecUp: MaterialButton? = null
    private var btnRemoteSecDown: MaterialButton? = null
    private var btnRemoteSecLeft: MaterialButton? = null
    private var btnRemoteSecRight: MaterialButton? = null

    private var targetMasterIp: String = ""
    private var connectionJob: Job? = null
    private var isConnected = false

    private var isVideoMode = false
    private var isOsdOn = false
    private var isPipMode = false
    private var currentOsdStyle = "box"

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "system") ?: "system"
        val config = Configuration(newBase.resources.configuration)

        if (savedLang != "system") {
            val locale = Locale(savedLang)
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

        // Blockiert das automatische Drehen und fixiert das Layout stabil im Portrait-Modus
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

        // Aktiviert den fliegenden und spiegelnden Benchy-Hintergrund (Index 0)
        applyFloatingBenchyBackground()

        // Aktiviert den vergrößerten Vignette-Hintergrundschatten im Vordergrund
        applyDynamicVignetteBorder()

        if (targetMasterIp.isNotEmpty()) {
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

        etTargetTvIp.keyListener = DigitsKeyListener.getInstance("0123456789.")
        tvFooterInfo.text = getString(R.string.remote_connected_printer, "K2")

        try {
            val bubbleContainer = etTargetTvIp.parent as? ViewGroup
            if (bubbleContainer != null) {
                val grandParent = bubbleContainer.parent as? ViewGroup
                if (grandParent != null && bubbleContainer.id != android.R.id.content) {
                    val density = resources.displayMetrics.density
                    val originalIndex = grandParent.indexOfChild(bubbleContainer)
                    val originalParams = bubbleContainer.layoutParams
                    val originalId = bubbleContainer.id

                    val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

                    val tvIpLabel = TextView(this).apply {
                        text = "TV IP"
                        textSize = 14f
                        setTextColor(if (isNightMode) Color.parseColor("#B0FFFFFF") else Color.parseColor("#8A000000"))
                        gravity = Gravity.CENTER
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val wrapperLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = originalParams
                        if (originalId != View.NO_ID) {
                            id = originalId
                        }
                    }

                    bubbleContainer.id = View.generateViewId()
                    grandParent.removeView(bubbleContainer)

                    val labelParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (24 * density).toInt()
                        bottomMargin = (10 * density).toInt()
                    }
                    tvIpLabel.layoutParams = labelParams

                    bubbleContainer.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                    wrapperLayout.addView(tvIpLabel)
                    wrapperLayout.addView(bubbleContainer)
                    grandParent.addView(wrapperLayout, originalIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Wrapper Label Injektion fehlgeschlagen", e)
        }
    }

    // UPDATE: Geschwindigkeit entkoppelt und auf super-sanften Zeitlupen-Modus gedrosselt
    private fun applyFloatingBenchyBackground() {
        try {
            val rootContent = findViewById<ViewGroup>(android.R.id.content)
            val inflatedLayout = rootContent?.getChildAt(0) as? ViewGroup

            if (rootContent != null && inflatedLayout != null) {
                val benchyView = ImageView(this).apply {
                    setImageResource(R.drawable.benchy_boat)
                    alpha = 0.06f
                }

                val originalXmlBg = inflatedLayout.background
                if (originalXmlBg != null) {
                    rootContent.background = originalXmlBg
                    inflatedLayout.background = null
                }

                rootContent.addView(benchyView, 0)

                lifecycleScope.launch(Dispatchers.Main) {
                    while (rootContent.width == 0 || rootContent.height == 0) {
                        delay(30)
                    }

                    val containerW = rootContent.width
                    val containerH = rootContent.height

                    // Boot füllt exakt 45% der Bildschirmbreite aus (Perfekt balanciert auf jedem Smartphone)
                    val boatSize = (containerW * 0.45f).toInt()
                    benchyView.layoutParams = FrameLayout.LayoutParams(boatSize, boatSize)

                    var posX = (containerW - boatSize) / 2f
                    var posY = containerH * 0.35f

                    // MAXIMUM DEZENT: Absolute, minimale Fließkommawandel verhindern jegliches Rasen auf High-Res-Displays
                    var speedX = 0.06f
                    var speedY = 0.045f

                    while (isActive) {
                        val currentW = rootContent.width
                        val currentH = rootContent.height

                        posX += speedX
                        posY += speedY

                        if (posX <= 0f) {
                            speedX = abs(speedX)
                            posX = 0f
                            benchyView.scaleX = 1f
                        } else if (posX + boatSize >= currentW) {
                            speedX = -abs(speedX)
                            posX = (currentW - boatSize).toFloat()
                            benchyView.scaleX = -1f
                        }

                        if (posY <= 0f) {
                            speedY = abs(speedY)
                            posY = 0f
                        } else if (posY + boatSize >= currentH) {
                            speedY = -abs(speedY)
                            posY = (currentH - boatSize).toFloat()
                        }

                        benchyView.x = posX
                        benchyView.y = posY

                        delay(16)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Benchy Live-Background fehlgeschlagen", e)
        }
    }

    private fun applyDynamicVignetteBorder() {
        try {
            val rootContent = findViewById<ViewGroup>(android.R.id.content)
            if (rootContent != null) {
                val vignetteView = View(this).apply {
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }

                vignetteView.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                    val w = right - left
                    val h = bottom - top
                    if (w > 0 && h > 0) {
                        val radius = sqrt((w * w + h * h).toDouble()).toFloat() * 0.72f
                        v.background = GradientDrawable().apply {
                            gradientType = GradientDrawable.RADIAL_GRADIENT
                            setColors(intArrayOf(
                                Color.TRANSPARENT,
                                Color.parseColor("#1F000000"),
                                Color.parseColor("#D9000000")
                            ))
                            gradientRadius = radius
                            setGradientCenter(0.5f, 0.5f)
                        }
                    }
                }
                rootContent.addView(vignetteView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Vignette Optimierung fehlgeschlagen", e)
        }
    }

    private fun setupListeners() {
        btnSearchTv.setOnClickListener {
            val ip = etTargetTvIp.text.toString().trim()
            if (ip.isNotEmpty()) {
                triggerConnectionCheck(ip)
            }
        }

        btnLeaveRemote.setOnClickListener {
            vibrateFeedback()
            finish()
        }

        btnRemoteUp.setOnClickListener { sendCommandToTv("DPAD_UP") }
        btnRemoteDown.setOnClickListener { sendCommandToTv("DPAD_DOWN") }
        btnRemoteLeft.setOnClickListener { sendCommandToTv("DPAD_LEFT") }
        btnRemoteRight.setOnClickListener { sendCommandToTv("DPAD_RIGHT") }
        btnRemoteOk.setOnClickListener { sendCommandToTv("DPAD_OK") }

        btnRemoteHome.setOnClickListener { sendCommandToTv("BACK") }
        btnRemoteZoomIn.setOnClickListener { sendCommandToTv("ZOOM_IN") }
        btnRemoteZoomOut.setOnClickListener { sendCommandToTv("ZOOM_OUT") }

        btnRemoteEstop.setOnClickListener {
            vibrateFeedback()

            val dialogContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val pad = (24 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 48f
                    setColor(Color.parseColor("#E53935"))
                }
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val tvTitle = TextView(this).apply {
                text = getString(R.string.menu_emergency_stop)
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvMessage = TextView(this).apply {
                text = getString(R.string.dialog_stop_title)
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (16 * resources.displayMetrics.density).toInt()
                }
            }

            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                weightSum = 2f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (24 * resources.displayMetrics.density).toInt()
                }
            }

            val pVert = (12 * resources.displayMetrics.density).toInt()

            val btnCancel = MaterialButton(this).apply {
                text = getString(R.string.cancel)
                isAllCaps = false
                textSize = 15f
                isFocusable = true
                setTextColor(Color.parseColor("#E53935"))
                backgroundTintList = ColorStateList.valueOf(Color.WHITE)
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, pVert, 0, pVert)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * resources.displayMetrics.density).toInt()
                }
            }

            val btnConfirm = MaterialButton(this).apply {
                text = getString(R.string.dialog_stop_confirm)
                isAllCaps = false
                textSize = 15f
                isFocusable = true
                setTextColor(Color.WHITE)
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#B71C1C"))
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, pVert, 0, pVert)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (8 * resources.displayMetrics.density).toInt()
                }
            }

            buttonRow.addView(btnCancel)
            buttonRow.addView(btnConfirm)
            dialogContainer.addView(tvTitle)
            dialogContainer.addView(tvMessage)
            buttonRow.let { dialogContainer.addView(it) }

            val dialog = AlertDialog.Builder(this)
                .setView(dialogContainer)
                .create()

            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val borderHighlight = if (isNight) Color.parseColor("#FFFFFF") else Color.parseColor("#424242")

            arrayOf(btnCancel, btnConfirm).forEach { btn ->
                btn.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(100).start()
                    if (v is MaterialButton) {
                        v.strokeWidth = if (hasFocus) 6 else 0
                        v.strokeColor = if (hasFocus) ColorStateList.valueOf(borderHighlight) else null
                    }
                }
            }

            btnCancel.setOnClickListener { dialog.dismiss() }
            btnConfirm.setOnClickListener {
                dialog.dismiss()
                sendCommandToTv("ESTOP")
            }

            dialog.show()
            btnCancel.requestFocus()
        }

        etTargetTvIp.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val currentIp = s.toString().trim()
                prefs.edit().putString("last_master_tv_ip", currentIp).apply()
            }
        })
    }

    private fun setupSpecialToggles() {
        updateButtonColorState(btnRemoteStream, isVideoMode)
        btnRemoteStream.setOnClickListener {
            vibrateFeedback()
            isVideoMode = !isVideoMode
            prefs.edit().putBoolean("remote_state_video", isVideoMode).apply()
            updateButtonColorState(btnRemoteStream, isVideoMode)
            sendCommandToTv("TOGGLE_VIDEO")
        }

        updateButtonColorState(btnRemotePip, isOsdOn)
        btnRemotePip.setOnClickListener {
            vibrateFeedback()
            isOsdOn = !isOsdOn
            prefs.edit().putBoolean("remote_state_osd", isOsdOn).apply()
            updateButtonColorState(btnRemotePip, isOsdOn)

            refreshOsdDependentUi()

            sendCommandToTv("TOGGLE_OSD")
        }

        updateButtonColorState(btnRemoteLayout, isPipMode)
        btnRemoteLayout.setOnClickListener {
            vibrateFeedback()
            isPipMode = !isPipMode
            prefs.edit().putBoolean("remote_state_pip", isPipMode).apply()
            updateButtonColorState(btnRemoteLayout, isPipMode)
            sendCommandToTv("PIP_TOGGLE")
        }

        btnRemoteBox.setOnClickListener {
            vibrateFeedback()
            currentOsdStyle = "box"
            prefs.edit().putString("remote_state_style", "box").apply()
            refreshOsdDependentUi()
            sendCommandToTv("STYLE_BOX")
        }

        btnRemoteBar.setOnClickListener {
            vibrateFeedback()
            currentOsdStyle = "banner"
            prefs.edit().putString("remote_state_style", "banner").apply()
            refreshOsdDependentUi()
            sendCommandToTv("STYLE_BANNER")
        }

        val secDirections = mapOf(
            "btnRemoteSecUp" to "SEC_UP",
            "btnRemoteSecDown" to "SEC_DOWN",
            "btnRemoteSecLeft" to "SEC_LEFT",
            "btnRemoteSecRight" to "SEC_RIGHT"
        )
        secDirections.forEach { (resName, token) ->
            val resId = resources.getIdentifier(resName, "id", packageName)
            if (resId != 0) {
                findViewById<MaterialButton>(resId)?.setOnClickListener {
                    vibrateFeedback()
                    if (isOsdOn) sendCommandToTv(token)
                }
            }
        }

        btnRemoteCamera.setOnClickListener { sendCommandToTv("CAMERA_CYCLE") }
    }

    private fun refreshOsdDependentUi() {
        val smallDpad = arrayOf(btnRemoteSecUp, btnRemoteSecDown, btnRemoteSecLeft, btnRemoteSecRight)

        if (isOsdOn) {
            smallDpad.forEach { btn ->
                btn?.isEnabled = true
                btn?.alpha = 1.0f
            }
            btnRemoteBox.isEnabled = true
            btnRemoteBar.isEnabled = true
            btnRemoteBox.alpha = 1.0f
            btnRemoteBar.alpha = 1.0f

            updateButtonColorState(btnRemoteBox, currentOsdStyle == "box")
            updateButtonColorState(btnRemoteBar, currentOsdStyle == "banner")
        } else {
            smallDpad.forEach { btn ->
                btn?.isEnabled = false
                btn?.alpha = 0.35f
            }

            btnRemoteBox.isEnabled = false
            btnRemoteBar.isEnabled = false
            btnRemoteBox.alpha = 0.35f
            btnRemoteBar.alpha = 0.35f

            val darkGrey = ColorStateList.valueOf(Color.parseColor("#424242"))
            btnRemoteBox.backgroundTintList = darkGrey
            btnRemoteBar.backgroundTintList = darkGrey
            btnRemoteBox.setTextColor(Color.parseColor("#80FFFFFF"))
            btnRemoteBar.setTextColor(Color.parseColor("#80FFFFFF"))
        }
    }

    private fun updateButtonColorState(button: MaterialButton, isActive: Boolean) {
        if (isActive) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
        }
        button.setTextColor(Color.WHITE)
    }

    private fun applyUnifiedButtonShapesAndFocus() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val borderHighlight = if (isNight) Color.parseColor("#4CAF50") else Color.parseColor("#424242")

        val allRemoteButtons = arrayOf(
            btnSearchTv, btnLeaveRemote, btnRemoteUp, btnRemoteDown, btnRemoteLeft, btnRemoteRight, btnRemoteOk,
            btnRemoteHome, btnRemoteStream, btnRemoteCamera, btnRemoteEstop, btnRemoteZoomIn, btnRemoteZoomOut,
            btnRemoteLayout, btnRemotePip, btnRemoteBar, btnRemoteBox
        )

        val combinedButtons = allRemoteButtons.toMutableList()
        val smallDpad = arrayOf(btnRemoteSecUp, btnRemoteSecDown, btnRemoteSecLeft, btnRemoteSecRight)
        smallDpad.forEach { btn -> btn?.let { combinedButtons.add(it) } }

        combinedButtons.forEach { btn ->
            if (btn == null) return@forEach
            btn.shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()

            if (btn == btnRemoteEstop) {
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
                btn.setTextColor(Color.WHITE)
            }

            val oldListener = btn.onFocusChangeListener
            btn.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                oldListener?.onFocusChange(v, hasFocus)
                if (v.isEnabled) {
                    v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(120).start()
                    if (v is MaterialButton) {
                        v.strokeWidth = if (hasFocus) 6 else 0
                        v.strokeColor = if (hasFocus) ColorStateList.valueOf(borderHighlight) else null
                    }
                }
            }
        }
    }

    private fun triggerConnectionCheck(ip: String) {
        connectionJob?.cancel()
        tvRemoteStatus.text = getString(R.string.toast_loading_dashboard)
        tvRemoteStatus.setTextColor(Color.parseColor("#FFD54F"))

        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, 9999), 3000)
                withContext(Dispatchers.Main) {
                    isConnected = true
                    targetMasterIp = ip
                    tvRemoteStatus.text = getString(R.string.remote_status_connected)
                    tvRemoteStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnected = false
                    tvRemoteStatus.text = getString(R.string.toast_no_connection)
                    tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun sendCommandToTv(command: String) {
        vibrateFeedback()
        if (!isConnected || targetMasterIp.isEmpty()) {
            showCenteredPillToast(getString(R.string.toast_no_connection))
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(targetMasterIp, 9999), 1500)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"))
                val payload = JSONObject().apply {
                    put("command", command)
                    put("source", "KlippShellSmartRemote")
                }
                writer.write(payload.toString() + "\n")
                writer.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isConnected = false
                    tvRemoteStatus.text = getString(R.string.remote_status_disconnected)
                    tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
                }
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun vibrateFeedback() {
        window.decorView.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return

        val backgroundColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val textColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val pillView = TextView(this).apply {
            text = message
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(backgroundColor)
                setStroke(4, textColor)
            }
            setPadding(40, 24, 40, 24)
        }
        val container = FrameLayout(this).apply {
            addView(pillView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(40, 0, 40, 180)
            })
        }
        rootLayout.addView(container)
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2000)
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        super.onDestroy()
    }
}