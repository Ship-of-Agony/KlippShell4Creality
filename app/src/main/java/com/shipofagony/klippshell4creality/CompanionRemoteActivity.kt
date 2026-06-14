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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

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

    private var targetMasterIp: String = ""
    private var connectionJob: Job? = null
    private var isConnected = false

    private var isVideoMode = false
    private var isOsdOn = false
    private var isPipMode = false
    private var currentOsdStyle = "box"

    // Erzwingt die korrekte Lokalisierung basierend auf SharedPreferences
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

        etTargetTvIp.keyListener = DigitsKeyListener.getInstance("0123456789.")

        // Dynamisch übersetzten Text mit Argument setzen
        tvFooterInfo.text = getString(R.string.remote_connected_printer, "K2")
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
        btnRemoteOk.setOnClickListener { sendCommandToTv("DPAD_CENTER") }

        btnRemoteHome.setOnClickListener { sendCommandToTv("BACK") }
        btnRemoteZoomIn.setOnClickListener { sendCommandToTv("ZOOM_OUT") }
        btnRemoteZoomOut.setOnClickListener { sendCommandToTv("ZOOM_IN") }

        btnRemoteEstop.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.menu_emergency_stop))
                .setMessage(getString(R.string.dialog_stop_title))
                .setPositiveButton(getString(R.string.dialog_stop_confirm)) { _, _ -> sendCommandToTv("ESTOP") }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
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
            isVideoMode = !isVideoMode
            prefs.edit().putBoolean("remote_state_video", isVideoMode).apply()
            updateButtonColorState(btnRemoteStream, isVideoMode)
            sendCommandToTv("TOGGLE_VIDEO")
        }

        updateButtonColorState(btnRemotePip, isOsdOn)
        btnRemotePip.setOnClickListener {
            isOsdOn = !isOsdOn
            prefs.edit().putBoolean("remote_state_osd", isOsdOn).apply()
            updateButtonColorState(btnRemotePip, isOsdOn)
            sendCommandToTv("TOGGLE_OSD")
        }

        updateButtonColorState(btnRemoteLayout, isPipMode)
        btnRemoteLayout.setOnClickListener {
            isPipMode = !isPipMode
            prefs.edit().putBoolean("remote_state_pip", isPipMode).apply()
            updateButtonColorState(btnRemoteLayout, isPipMode)
            sendCommandToTv("PIP_TOGGLE")
        }

        updateButtonColorState(btnRemoteBox, currentOsdStyle == "box")
        updateButtonColorState(btnRemoteBar, currentOsdStyle == "banner")

        btnRemoteBox.setOnClickListener {
            currentOsdStyle = "box"
            prefs.edit().putString("remote_state_style", "box").apply()
            updateButtonColorState(btnRemoteBox, true)
            updateButtonColorState(btnRemoteBar, false)
            sendCommandToTv("STYLE_BOX")
        }

        btnRemoteBar.setOnClickListener {
            currentOsdStyle = "banner"
            prefs.edit().putString("remote_state_style", "banner").apply()
            updateButtonColorState(btnRemoteBox, false)
            updateButtonColorState(btnRemoteBar, true)
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
                    if (isOsdOn) sendCommandToTv(token) else sendCommandToTv(token.replace("SEC_", "DPAD_"))
                }
            }
        }

        btnRemoteCamera.setOnClickListener { sendCommandToTv("CAMERA_CYCLE") }
    }

    private fun updateButtonColorState(button: MaterialButton, isActive: Boolean) {
        if (isActive) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
        }
        button.setTextColor(Color.WHITE)
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