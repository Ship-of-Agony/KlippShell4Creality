package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.SharedPreferences
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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private var radarJob: Job? = null
    private var isManualOverrideActive = false
    private var textWatcher: TextWatcher? = null

    private var lastCommandTime = 0L
    private val commandCooldownMs = 150L

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "de") ?: "de"
        val locale = Locale.forLanguageTag(savedLang)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_companion_remote)

        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        etTargetTvIp = findViewById(R.id.etTargetTvIp)
        tvRemoteStatus = findViewById(R.id.tvRemoteStatus)

        etTargetTvIp.keyListener = DigitsKeyListener.getInstance("0123456789.")

        val savedIp = prefs.getString("saved_target_tv_ip", "") ?: ""
        if (savedIp.isNotEmpty()) {
            etTargetTvIp.setText(savedIp)
            validateAndRefreshStatus(savedIp, null, null)
        }

        textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val inputIp = s.toString().trim()
                isManualOverrideActive = inputIp.isNotEmpty()
                prefs.edit().putString("saved_target_tv_ip", inputIp).apply()
                validateAndRefreshStatus(inputIp, null, null)
            }
        }
        etTargetTvIp.addTextChangedListener(textWatcher)

        findViewById<View>(R.id.btnSearchTv)?.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCenteredPillToast("Netzwerk-Scan gestartet...")
            startTvAutoRadar(forceScan = true)
        }

        tvRemoteStatus.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showCenteredPillToast("Netzwerk-Scan gestartet...")
            startTvAutoRadar(forceScan = true)
        }

        findViewById<View>(R.id.btnLeaveRemote)?.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            finish()
        }

        val remoteButtonsMap = mapOf(
            "btnRemoteUp" to "DPAD_UP",
            "btnRemoteDown" to "DPAD_DOWN",
            "btnRemoteLeft" to "DPAD_LEFT",
            "btnRemoteRight" to "DPAD_RIGHT",
            "btnRemoteOk" to "DPAD_OK",
            "btnRemoteBack" to "BACK",
            "btnRemoteZoomIn" to "ZOOM_IN",
            "btnRemoteZoomOut" to "ZOOM_OUT",
            "btnRemoteLayout" to "LAYOUT",
            "btnRemoteStream" to "STREAM",
            "btnRemotePip" to "PIP",
            "btnRemoteLight" to "LIGHT",
            "btnRemoteEStop" to "ESTOP",
            "btnRemoteBar" to "BAR_TOGGLE",
            "btnRemoteBox" to "BOX_TOGGLE",
            "btnRemoteSecUp" to "SEC_UP",
            "btnRemoteSecDown" to "SEC_DOWN",
            "btnRemoteSecLeft" to "SEC_LEFT",
            "btnRemoteSecRight" to "SEC_RIGHT",
            "btnRemoteCamera" to "CAMERA_SWITCH"
        )

        remoteButtonsMap.forEach { (resName, befehl) ->
            val resId = resources.getIdentifier(resName, "id", packageName)
            if (resId != 0) {
                findViewById<View>(resId)?.let { view ->
                    if (view is MaterialButton) {
                        setupRemoteButton(view, befehl)
                    } else {
                        view.setOnClickListener { v ->
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            transmitBefehl(befehl)
                        }
                    }
                }
            }
        }

        if (savedIp.isEmpty()) {
            startTvAutoRadar(forceScan = false)
        }
    }

    private fun setupRemoteButton(button: MaterialButton, befehl: String) {
        button.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).withEndAction {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
            }.start()
            transmitBefehl(befehl)
        }
    }

    private fun validateAndRefreshStatus(ip: String, printerName: String?, printerModel: String?) {
        val cleanIp = ip.trim()
        val ipRegex = "^([0-9]{1,3}\\.){3}[0-9]{1,3}$".toRegex()
        val tvFooterInfo = findViewById<TextView>(R.id.tvFooterInfo)

        if (cleanIp.isNotEmpty() && ipRegex.matches(cleanIp)) {
            tvRemoteStatus.text = "Verbunden"
            if (printerName != null && printerModel != null) {
                tvFooterInfo?.text = "Verbundener Drucker: $printerName ($printerModel) • $cleanIp"
            } else {
                tvFooterInfo?.text = "KlippShell TV • $cleanIp"
            }
            tvRemoteStatus.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            tvRemoteStatus.text = "Getrennt"
            tvRemoteStatus.setTextColor(Color.parseColor("#E53935"))
            tvFooterInfo?.text = "Kein Drucker verbunden"
        }
    }

    private fun startTvAutoRadar(forceScan: Boolean = false) {
        radarJob?.cancel()
        radarJob = lifecycleScope.launch(Dispatchers.IO) {
            val localIp = getLocalIpAddress() ?: return@launch
            val ipPrefix = localIp.substringBeforeLast(".")
            val semaphore = Semaphore(30)
            var tvFoundIp: String? = null
            var tvPrinterName: String? = null
            var tvPrinterModel: String? = null

            val jobs = (1..254).map { i ->
                val targetHost = "$ipPrefix.$i"
                if (targetHost == localIp) return@map launch {}

                launch {
                    semaphore.withPermit {
                        if (tvFoundIp != null || (!forceScan && isManualOverrideActive)) return@launch
                        var socket: Socket? = null
                        try {
                            socket = Socket()
                            socket.connect(InetSocketAddress(targetHost, 9999), 250)

                            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))

                            writer.write("REQUEST_PRINTER_INFO")
                            writer.newLine()
                            writer.flush()

                            val response = reader.readLine()?.trim()
                            if (!response.isNullOrEmpty()) {
                                val json = JSONObject(response)
                                tvFoundIp = targetHost
                                tvPrinterName = json.optString("name", "KlippShell TV")
                                tvPrinterModel = json.optString("model", "Drucker")
                                cancel()
                            }
                        } catch (_: Exception) {
                        } finally {
                            try { socket?.close() } catch (_: Exception) {}
                        }
                    }
                }
            }
            jobs.joinAll()

            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed && tvFoundIp != null) {
                    if (forceScan) {
                        isManualOverrideActive = false
                    }
                    etTargetTvIp.removeTextChangedListener(textWatcher)
                    etTargetTvIp.setText(tvFoundIp)
                    etTargetTvIp.addTextChangedListener(textWatcher)
                    prefs.edit().putString("saved_target_tv_ip", tvFoundIp).apply()
                    validateAndRefreshStatus(tvFoundIp!!, tvPrinterName, tvPrinterModel)
                    showCenteredPillToast("KlippShell TV gefunden! ✓")
                } else if (forceScan && tvFoundIp == null) {
                    showCenteredPillToast("Kein KlippShell TV im Netzwerk gefunden.")
                }
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in java.util.Collections.list(interfaces)) {
                for (addr in java.util.Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun transmitBefehl(befehlString: String) {
        val currentTime = System.currentTimeMillis()
        if (befehlString != "ESTOP" && (currentTime - lastCommandTime) < commandCooldownMs) {
            return
        }
        lastCommandTime = currentTime

        val targetIp = etTargetTvIp.text.toString().trim()
        if (targetIp.isEmpty()) {
            showCenteredPillToast("Bitte zuerst TV IP eintragen!")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var socket: Socket? = null
            var success = false
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(targetIp, 9999), 1200)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                writer.write(befehlString)
                writer.newLine()
                writer.flush()
                success = true
            } catch (e: Exception) {
                Log.e("KlippShell", "Failed transmitting token: $befehlString to $targetIp", e)
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }

            if (!success) {
                withContext(Dispatchers.Main) {
                    showCenteredPillToast("Verbindungsfehler zu TV: $targetIp")
                    if (!isManualOverrideActive) startTvAutoRadar(forceScan = false)
                }
            }
        }
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pillView = TextView(this).apply {
            text = message
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(Color.parseColor(if (isNight) "#252B2E" else "#FFFFFF"))
                setStroke(4, Color.parseColor(if (isNight) "#FFFFFF" else "#BDBDBD"))
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
        radarJob?.cancel()
        super.onDestroy()
    }
}