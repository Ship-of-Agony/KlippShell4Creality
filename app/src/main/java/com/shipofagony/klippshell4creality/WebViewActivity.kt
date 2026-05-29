package com.shipofagony.klippshell4creality

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
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var layoutOsd: View
    private lateinit var layoutWebButtons: LinearLayout

    private var currentActiveUrl: String = ""
    private var isCameraMode: Boolean = false
    private var isOsdEnabled: Boolean = false

    private var knownChamberSensor: String? = "temperature_sensor chamber"
    private var cachedChamberSensorString: String = "&temperature_sensor%20chamber"

    private var chamberSearchIndex = 0
    private val chamberNamesToTry = listOf(
        "temperature_sensor chamber",
        "temperature_sensor chamber_temp",
        "heater_generic chamber"
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
    private val IMMERSIVE_TIMEOUT = 5000L

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
                                      '*::-webkit-scrollbar-thumb { background-color: #2196F3 !important; border-radius: 9px !important; }';
                    document.head.appendChild(style);
                    
                    function makeDpadFriendly() {
                        var items = document.querySelectorAll('.v-list-item, .v-btn, a, button, input');
                        items.forEach(function(item) {
                            if (!item.hasAttribute('tabindex')) item.setAttribute('tabindex', '0');
                        });
                    }
                    
                    makeDpadFriendly(); 
                    
                    var observer = new MutationObserver(function(mutations) {
                        mutations.forEach(function(mutation) {
                            if (mutation.addedNodes.length > 0) {
                                makeDpadFriendly();
                            }
                        });
                    });
                    
                    observer.observe(document.body, { childList: true, subtree: true });
                """.trimIndent()
                view?.evaluateJavascript(jsInjection, null)
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.isVerticalScrollBarEnabled = true
        webView.isScrollbarFadingEnabled = false
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false
        }

        currentActiveUrl = intent.getStringExtra("TARGET_URL") ?: "http://google.com"

        val initialIp = Uri.parse(currentActiveUrl).host ?: ""
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedRatio = try { prefs.getFloat("camera_ratio_$initialIp", 56.25f) } catch(e: Exception) { 56.25f }

        loadStreamOrUrl(currentActiveUrl, savedRatio)
        showButtons()

        btnClose.setOnClickListener { finish() }

        btnMenu.setOnClickListener {
            showButtons()
            val hostIp = Uri.parse(currentActiveUrl).host ?: ""
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
                        layoutOsd.visibility = if (isOsdEnabled && isCameraMode) View.VISIBLE else View.GONE
                        if (isOsdEnabled) {
                            osdHandler.removeCallbacks(osdRunnable)
                            osdHandler.post(osdRunnable)
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
            val hostIp = Uri.parse(currentActiveUrl).host ?: ""
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
                // HIER WURDE INTERFACE ZU DASHBOARD GEÄNDERT
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
        uiHandler.postDelayed(hideUiRunnable, IMMERSIVE_TIMEOUT)
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
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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

        val currentChamberQuery = if (knownChamberSensor != null) cachedChamberSensorString else ""

        val urlsToTry = listOf("http://$hostIp:7125/$baseQuery$currentChamberQuery", "http://$hostAuthority/$baseQuery$currentChamberQuery")

        Thread {
            var responseText = ""
            var lastErrorMsg = ""
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
                    } else if (code == 400 && knownChamberSensor != null) {
                        gotBadRequest = true
                        break
                    } else { lastErrorMsg = "HTTP Error: $code" }
                } catch (e: Exception) { lastErrorMsg = e.message ?: "Connection failed" }
            }

            if (gotBadRequest) {
                chamberSearchIndex++
                if (chamberSearchIndex < chamberNamesToTry.size) {
                    knownChamberSensor = chamberNamesToTry[chamberSearchIndex]
                    cachedChamberSensorString = "&${knownChamberSensor!!.replace(" ", "%20")}"
                } else {
                    knownChamberSensor = null
                    cachedChamberSensorString = ""
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

                            findViewById<TextView>(R.id.tvOsdExtruder).text = String.format("Düse: %.1f°C / %.0f°C", tempExtruder, targetExtruder)
                            findViewById<TextView>(R.id.tvOsdBed).text = String.format("Bett: %.1f°C / %.0f°C", tempBed, targetBed)
                            findViewById<TextView>(R.id.tvOsdProgress).text = String.format("%.1f%%", progress * 100)

                            val passedMin = duration / 60
                            val passedSec = duration % 60
                            val totalStr = if (progress > 0.001) {
                                val totalTimeEstimated = (duration / progress).toInt()
                                String.format("%02d:%02d", totalTimeEstimated / 60, totalTimeEstimated % 60)
                            } else { "--:--" }
                            findViewById<TextView>(R.id.tvOsdTime).text = "Zeit: ${String.format("%02d:%02d", passedMin, passedSec)} / $totalStr"

                            val tvChamber = findViewById<TextView?>(R.id.tvOsdChamber)
                            if (tvChamber != null) {
                                if (knownChamberSensor != null) {
                                    val chamberData = status.optJSONObject(knownChamberSensor)
                                    val tempChamber = chamberData?.optDouble("temperature", 0.0) ?: 0.0
                                    val targetChamber = chamberData?.optDouble("target", 0.0) ?: 0.0
                                    if (tempChamber > 0.0) {
                                        tvChamber.text = if (targetChamber > 0.0) String.format("Kammer: %.1f°C / %.0f°C", tempChamber, targetChamber) else String.format("Kammer: %.1f°C", tempChamber)
                                        tvChamber.visibility = View.VISIBLE
                                    } else { tvChamber.visibility = View.GONE }
                                } else { tvChamber.visibility = View.GONE }
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        findViewById<TextView>(R.id.tvOsdExtruder).text = "JSON Fehler"
                        findViewById<TextView>(R.id.tvOsdBed).text = e.message ?: "Parser Error"
                    }
                }
            } else {
                runOnUiThread {
                    findViewById<TextView>(R.id.tvOsdExtruder).text = getString(R.string.osd_no_connection)
                    findViewById<TextView>(R.id.tvOsdBed).text = lastErrorMsg
                }
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (layoutWebButtons.alpha < 1f) {
                showButtons()
                findViewById<MaterialButton>(R.id.btnWebClose).requestFocus()
                return true
            }
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