package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.RingtoneManager
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

    // Start-Konfiguration (optimiert auf dein K2 Plus Setup aus der printer.cfg)
    private var knownChamberSensor: String? = "temperature_sensor chamber_temp"
    private var knownChamberHeater: String? = "heater_generic chamber_heater"
    private var cachedChamberQueryString: String = "&temperature_sensor%20chamber_temp&heater_generic%20chamber_heater"

    private var chamberSearchIndex = 0

    // Universelle Community-Namen-Weiche
    private val chamberSensorsToTry = listOf(
        "temperature_sensor chamber_temp",   // K2 Plus Standard
        "temperature_sensor chamber",        // K1 / K1 Max / Qidi Standard
        "temperature_sensor enclosure_temp", // Voron / RatRig Community Standard
        "temperature_sensor enclosure",      // Custom Einbauten
        "temperature_sensor ambient",        // Verglaste Bettschubser
        "temperature_sensor frame_temp"      // Thermische Kompensation
    )

    private val chamberHeatersToTry = listOf(
        "heater_generic chamber_heater",     // K2 Plus Standard
        "heater_generic chamber",            // Qidi / Custom CoreXY
        "heater_generic enclosure_heater",   // Industrielle Nachrüstsätze
        "enclosure_heater",                  // Kurzform Makro-Pakete
        "heater_generic chamber_ptc",        // Voron PTC-Mod
        "heater_generic ambient_heater"      // Exotischer Fallback
    )

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
                if (view is MaterialButton) {
                    view.strokeWidth = 0
                }
            }
        }

        val dpadUpListener = View.OnKeyListener { _, keyCode, event ->
            showButtons()
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                webView.requestFocus()
                return@OnKeyListener true
            }
            false
        }

        arrayOf(btnMenu, btnToggle, btnClose).forEach { btn ->
            btn.isFocusable = true
            btn.alpha = 0.8f
            btn.onFocusChangeListener = tvFocusListener
            btn.setOnKeyListener(dpadUpListener)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val jsInjection = """
                    var style = document.createElement('style');
                    style.innerHTML = '*:focus { outline: 4px solid #FFFFFF !important; outline-offset: -2px !important; background-color: rgba(255, 255, 255, 0.15) !important; border-radius: 4px !important; } ' +
                                      '::-webkit-scrollbar { width: 18px !important; display: block !important; } ' +
                                      '::-webkit-scrollbar-track { background: transparent !important; } ' +
                                      '::-webkit-scrollbar-thumb { background-color: #2196F3 !important; border-radius: 9px !important; } ' +
                                      '*::-webkit-scrollbar { width: 18px !important; display: block !important; } ' +
                                      '*::-webkit-scrollbar-track { background: transparent !important; } ' +
                                      '*::-webkit-scrollbar-thumb { background-color: #2196F3 !important; border-radius: 9px !important; } ' +
                                      '.v-navigation-drawer::-webkit-scrollbar { display: none !important; width: 0px !important; } ' +
                                      '.v-navigation-drawer *::-webkit-scrollbar { display: none !important; width: 0px !important; }';
                    document.head.appendChild(style);
                """.trimIndent()
                view?.evaluateJavascript(jsInjection, null)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }

        webView.isVerticalScrollBarEnabled = true
        webView.isScrollbarFadingEnabled = false
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
            allowContentAccess = true
            allowFileAccess = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
        }

        currentActiveUrl = intent.getStringExtra("TARGET_URL") ?: "http://google.com"

        val hostIp = Uri.parse(currentActiveUrl).host ?: ""
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedRatio = try { prefs.getFloat("camera_ratio_$hostIp", 56.25f) } catch(e: Exception) { 56.25f }

        // NEU: OSD-Zustand für diesen spezifischen Drucker aus den SharedPreferences laden
        isOsdEnabled = prefs.getBoolean("osd_enabled_$hostIp", false)

        loadStreamOrUrl(currentActiveUrl, savedRatio)
        showButtons()

        btnClose.setOnClickListener { finish() }

        btnMenu.setOnClickListener {
            showButtons()
            val optionsList = mutableListOf<String>()

            val strOsdShow = getString(R.string.menu_osd_show)
            val strOsdHide = getString(R.string.menu_osd_hide)
            val strRatio = getString(R.string.menu_ratio)
            val strCamType = getString(R.string.menu_change_camera_type)
            val strEmergency = getString(R.string.menu_emergency_stop)

            if (isCameraMode) {
                optionsList.add(if (isOsdEnabled) strOsdHide else strOsdShow)
                optionsList.add(strRatio)
                optionsList.add(strCamType)
            }
            optionsList.add(strEmergency)

            showModernMenu(getString(R.string.menu_options_title), optionsList.toTypedArray()) { selectedIndex ->
                showButtons()
                val chosenOption = optionsList[selectedIndex]

                when (chosenOption) {
                    strOsdShow, strOsdHide -> {
                        isOsdEnabled = !isOsdEnabled

                        // NEU: Zustand sofort persistent für diesen Drucker abspeichern
                        getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).edit()
                            .putBoolean("osd_enabled_$hostIp", isOsdEnabled).apply()

                        layoutOsd.visibility = if (isOsdEnabled && isCameraMode) View.VISIBLE else View.GONE
                        if (isOsdEnabled) {
                            osdHandler.removeCallbacks(osdRunnable)
                            osdHandler.post(osdRunnable)
                        } else {
                            osdHandler.removeCallbacks(osdRunnable)
                        }
                    }
                    strRatio -> {
                        showModernMenu("Format", arrayOf("1:1", "16:9", "4:3")) { ratioIndex ->
                            val ratio = when (ratioIndex) {
                                0 -> 100f
                                1 -> 56.25f
                                else -> 75f
                            }
                            getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).edit().putFloat("camera_ratio_$hostIp", ratio).apply()
                            loadStreamOrUrl(currentActiveUrl, ratio)
                        }
                    }
                    strCamType -> {
                        val camOptions = arrayOf(getString(R.string.camera_type_html), getString(R.string.camera_type_port), getString(R.string.camera_type_webcam))
                        showModernMenu(strCamType, camOptions) { camIndex ->
                            val typeString = when (camIndex) {
                                1 -> "port"
                                2 -> "webcam"
                                else -> "html"
                            }
                            getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).edit().putString("camera_type_$hostIp", typeString).apply()

                            val newUrl = when (typeString) {
                                "port" -> "http://$hostIp:8080/?action=stream"
                                "webcam" -> "http://$hostIp/webcam/?action=stream"
                                else -> "http://$hostIp/camera.html"
                            }
                            loadStreamOrUrl(newUrl, getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).getFloat("camera_ratio_$hostIp", 56.25f))
                        }
                    }
                    strEmergency -> {
                        showModernMenu(getString(R.string.dialog_stop_title), arrayOf(getString(R.string.dialog_stop_confirm), getString(R.string.dialog_cancel))) { confirmIndex ->
                            if (confirmIndex == 0) {
                                sendEmergencyStop()
                            }
                        }
                    }
                }
            }
        }

        btnToggle.setOnClickListener {
            showButtons()
            val currentPrefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

            if (!isCameraMode) {
                val savedType = currentPrefs.getString("camera_type_$hostIp", "html") ?: "html"
                val cameraUrl = when (savedType) {
                    "port" -> "http://$hostIp:8080/?action=stream"
                    "webcam" -> "http://$hostIp/webcam/?action=stream"
                    else -> "http://$hostIp/camera.html"
                }
                loadStreamOrUrl(cameraUrl, currentPrefs.getFloat("camera_ratio_$hostIp", 56.25f))
            } else {
                showModernMenu("Dashboard", arrayOf("Standard", "Port 4408")) { subWhich ->
                    loadStreamOrUrl(if (subWhich == 0) "http://$hostIp" else "http://$hostIp:4408", 0f)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        osdHandler.removeCallbacks(osdRunnable)
        uiHandler.removeCallbacks(hideUiRunnable)
    }

    private fun showButtons() {
        if (layoutWebButtons.alpha < 1f) {
            layoutWebButtons.animate().alpha(1f).setDuration(250).start()
            for (i in 0 until layoutWebButtons.childCount) layoutWebButtons.getChildAt(i).isClickable = true
        }
        uiHandler.removeCallbacks(hideUiRunnable)
        uiHandler.postDelayed(hideUiRunnable, immersiveTimeout)
    }

    private fun hideButtons() {
        layoutWebButtons.animate().alpha(0f).setDuration(500).start()
        for (i in 0 until layoutWebButtons.childCount) layoutWebButtons.getChildAt(i).isClickable = false
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        showButtons()
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        currentActiveUrl = url
        val isMjpegStream = url.contains("action=stream")
        val isHtmlCamera = url.contains("camera.html")
        isCameraMode = isMjpegStream || isHtmlCamera

        if (isCameraMode) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            layoutOsd.visibility = if (isOsdEnabled) View.VISIBLE else View.GONE
            if (isOsdEnabled) {
                osdHandler.removeCallbacks(osdRunnable)
                osdHandler.post(osdRunnable)
            }
            showButtons()

            val mediaElement = if (isHtmlCamera) {
                "<iframe src=\"$url\" scrolling=\"no\" style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; overflow: hidden;\"></iframe>"
            } else {
                "<img src=\"$url\" style=\"position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: fill;\" />"
            }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        body { margin: 0; padding: 0; background-color: #000000; display: flex; justify-content: center; align-items: center; height: 100vh; overflow: hidden; }
                        .container { position: relative; width: 100%; height: 0; padding-top: ${paddingTopPercent}%; }
                    </style>
                </head>
                <body> <div class="container"> $mediaElement </div> </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
        } else {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            layoutOsd.visibility = View.GONE
            osdHandler.removeCallbacks(osdRunnable)
            showButtons()
            webView.loadUrl(url)
        }
    }

    private fun fetchMoonrakerData() {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        val hostAuthority = uri.authority ?: hostIp
        val baseQuery = "printer/objects/query?extruder&heater_bed&print_stats&display_status"

        val urlsToTry = listOf(
            "http://$hostIp:7125/$baseQuery$cachedChamberQueryString",
            "http://$hostAuthority/$baseQuery$cachedChamberQueryString"
        )

        Thread {
            var responseText = ""
            var gotBadRequest = false

            for (urlStr in urlsToTry) {
                try {
                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val code = conn.responseCode
                    if (code == 200) {
                        responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        break
                    } else if (code == 400) {
                        gotBadRequest = true
                        break
                    }
                } catch (_: Exception) {}
            }

            if (gotBadRequest) {
                chamberSearchIndex++
                if (chamberSearchIndex < chamberSensorsToTry.size) {
                    knownChamberSensor = chamberSensorsToTry[chamberSearchIndex]
                    knownChamberHeater = chamberHeatersToTry[chamberSearchIndex]
                    cachedChamberQueryString = "&${knownChamberSensor!!.replace(" ", "%20")}&${knownChamberHeater!!.replace(" ", "%20")}"
                } else {
                    knownChamberSensor = null
                    knownChamberHeater = null
                    cachedChamberQueryString = ""
                }
                fetchMoonrakerData()
                return@Thread
            }

            if (responseText.isNotEmpty()) {
                try {
                    val json = JSONObject(responseText)
                    val status = json.optJSONObject("result")?.optJSONObject("status")

                    if (status != null) {
                        val extruder = status.optJSONObject("extruder")
                        val tempExtruder = extruder?.optDouble("temperature", 0.0) ?: 0.0
                        val targetExtruder = extruder?.optDouble("target", 0.0) ?: 0.0

                        val bed = status.optJSONObject("heater_bed")
                        val tempBed = bed?.optDouble("temperature", 0.0) ?: 0.0
                        val targetBed = bed?.optDouble("target", 0.0) ?: 0.0

                        val displayStatus = status.optJSONObject("display_status")
                        val progress = displayStatus?.optDouble("progress", 0.0) ?: 0.0

                        val printStats = status.optJSONObject("print_stats")
                        val duration = printStats?.optInt("print_duration", 0) ?: 0
                        val currentState = printStats?.optString("state", "") ?: ""

                        runOnUiThread {
                            if (lastPrintState.isNotEmpty() && lastPrintState != currentState) {
                                if (currentState == "complete") {
                                    playSystemSound(false)
                                    showCenteredPillToast("Druck erfolgreich beendet!")
                                } else if (currentState == "error") {
                                    playSystemSound(true)
                                    showCenteredPillToast("ACHTUNG: Klipper Fehler!")
                                }
                            }
                            lastPrintState = currentState

                            findViewById<TextView>(R.id.tvOsdExtruder).text = getString(R.string.osd_extruder, tempExtruder, targetExtruder)
                            findViewById<TextView>(R.id.tvOsdBed).text = getString(R.string.osd_bed, tempBed, targetBed)
                            findViewById<TextView>(R.id.tvOsdProgress).text = String.format(Locale.getDefault(), "%.1f%%", progress * 100)

                            val passedMin = duration / 60
                            val passedSec = duration % 60
                            val totalStr = if (progress > 0.001) {
                                val totalTimeEstimated = (duration / progress).toInt()
                                String.format(Locale.getDefault(), "%02d:%02d", totalTimeEstimated / 60, totalTimeEstimated % 60)
                            } else { "--:--" }

                            findViewById<TextView>(R.id.tvOsdTime).text = getString(R.string.osd_time, String.format(Locale.getDefault(), "%02d:%02d", passedMin, passedSec), totalStr)

                            val tvChamber = findViewById<TextView>(R.id.tvOsdChamber)
                            if (knownChamberSensor != null) {
                                val chamberData = status.optJSONObject(knownChamberSensor!!)
                                val tempChamber = chamberData?.optDouble("temperature", 0.0) ?: 0.0
                                if (tempChamber > 0.0) {
                                    tvChamber.text = getString(R.string.osd_chamber_sensor, tempChamber)
                                    tvChamber.visibility = View.VISIBLE
                                } else { tvChamber.visibility = View.GONE }
                            } else { tvChamber.visibility = View.GONE }

                            val tvChamberHeater = findViewById<TextView>(R.id.tvOsdChamberHeater)
                            if (knownChamberHeater != null) {
                                val heaterData = status.optJSONObject(knownChamberHeater!!)
                                val tempHeater = heaterData?.optDouble("temperature", 0.0) ?: 0.0
                                val targetHeater = heaterData?.optDouble("target", 0.0) ?: 0.0
                                if (tempHeater > 0.0) {
                                    tvChamberHeater.text = getString(R.string.osd_chamber_heater, tempHeater, targetHeater)
                                    tvChamberHeater.visibility = View.VISIBLE
                                } else { tvChamberHeater.visibility = View.GONE }
                            } else { tvChamberHeater.visibility = View.GONE }
                        }
                    }
                } catch (_: Exception) {}
            }
        }.start()
    }

    private fun sendEmergencyStop() {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        val urlsToTry = listOf("http://$hostIp:7125/printer/emergency_stop", "http://${uri.authority ?: hostIp}/printer/emergency_stop")

        Thread {
            var success = false
            var lastResponseCode = -1
            for (urlStr in urlsToTry) {
                try {
                    val conn = URL(urlStr).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3000
                    lastResponseCode = conn.responseCode
                    if (lastResponseCode in 200..299) { success = true; break }
                } catch (_: Exception) {}
            }
            runOnUiThread {
                if (success) showCenteredPillToast(getString(R.string.toast_stop_success))
                else showCenteredPillToast(if (lastResponseCode != -1) getString(R.string.toast_stop_error) + lastResponseCode else getString(R.string.toast_no_connection))
            }
        }.start()
    }

    private fun playSystemSound(isError: Boolean) {
        try {
            val alertType = if (isError) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            val uri = RingtoneManager.getDefaultUri(alertType)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone.play()
            if (isError) {
                Handler(Looper.getMainLooper()).postDelayed({ if (ringtone.isPlaying) ringtone.stop() }, 3000)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val pillDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            if (isNight) {
                setColor(Color.parseColor("#252B2E"))
                setStroke(4, Color.WHITE)
            } else {
                setColor(Color.WHITE)
                setStroke(4, Color.parseColor("#BDBDBD"))
            }
        }

        val pillView = TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = pillDrawable
            setPadding(50, 35, 50, 35)
            elevation = 12f
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(50, 0, 50, 240)
            }
        }
        container.addView(pillView)
        rootLayout.addView(container)
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2200)
    }

    private fun showModernMenu(title: String, items: Array<String>, onItemSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK
        val buttonBgColor = if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")
        val strEmergency = getString(R.string.menu_emergency_stop)

        items.forEachIndexed { index, itemText ->
            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                cornerRadius = 100
                setPadding(0, 35, 0, 35)

                if (itemText == strEmergency || itemText == getString(R.string.dialog_stop_confirm)) {
                    val redColorHex = if (isNightMode) "#C62828" else "#D32F2F"
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(redColorHex))
                    setTextColor(Color.WHITE)
                    strokeWidth = 0
                } else {
                    backgroundTintList = ColorStateList.valueOf(buttonBgColor)
                    setTextColor(textColor)
                }

                isFocusable = true

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 10, 0, 10)
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        strokeWidth = 6
                        strokeColor = if (itemText == strEmergency || itemText == getString(R.string.dialog_stop_confirm)) {
                            ColorStateList.valueOf(Color.parseColor("#FF5252"))
                        } else {
                            ColorStateList.valueOf(Color.WHITE)
                        }
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start()
                        strokeWidth = 0
                    }
                }
                setOnClickListener { onItemSelected(index); dialog.dismiss() }
            }
            container.addView(btn)
        }
        dialog.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && webView.hasFocus()) {
                showButtons()
                findViewById<MaterialButton>(R.id.btnWebClose).requestFocus()
                return true
            }

            if (isCameraMode && layoutWebButtons.alpha < 1f) {
                val keyCode = event.keyCode
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER) {

                    showButtons()
                    findViewById<MaterialButton>(R.id.btnWebClose).requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("MissingSuperCall")
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!isCameraMode && webView.canGoBack()) {
                webView.goBack()
                return true
            }
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}