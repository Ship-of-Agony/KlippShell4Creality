package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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

    private var currentActiveUrl: String = ""
    private var isCameraMode: Boolean = false
    private var isOsdEnabled: Boolean = false

    private var knownChamberSensor: String? = "temperature_sensor chamber"
    private var chamberSearchIndex = 0
    private val chamberNamesToTry = listOf(
        "temperature_sensor chamber",
        "temperature_sensor chamber_temp",
        "heater_generic chamber"
    )

    // NEU: Kurzzeitgedächtnis für den Druckstatus
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webView)
        layoutOsd = findViewById(R.id.layoutOsd)

        val btnMenu = findViewById<MaterialButton>(R.id.btnWebMenu)
        val btnToggle = findViewById<MaterialButton>(R.id.btnWebToggle)
        val btnClose = findViewById<MaterialButton>(R.id.btnWebClose)

        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
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
                    style.innerHTML = '*:focus { outline: 4px solid #FFFFFF !important; outline-offset: -2px !important; background-color: rgba(255, 255, 255, 0.15) !important; border-radius: 4px !important; }';
                    document.head.appendChild(style);
                    
                    var retryCount = 0;
                    var interval = setInterval(function() {
                        var items = document.querySelectorAll('.v-list-item, .v-btn, a, button, input');
                        items.forEach(function(item) {
                            if (!item.hasAttribute('tabindex')) {
                                item.setAttribute('tabindex', '0');
                            }
                        });
                        retryCount++;
                        if (retryCount > 5) clearInterval(interval);
                    }, 1000);
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

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        currentActiveUrl = intent.getStringExtra("TARGET_URL") ?: "http://google.com"

        val initialIp = Uri.parse(currentActiveUrl).host ?: ""
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedRatio = prefs.getFloat("camera_ratio_$initialIp", 56.25f)

        loadStreamOrUrl(currentActiveUrl, savedRatio)

        btnClose.setOnClickListener { finish() }

        btnMenu.setOnClickListener {
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
                            getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).edit()
                                .putFloat("camera_ratio_$hostIp", ratio).apply()

                            loadStreamOrUrl(currentActiveUrl, ratio)
                        }
                    }
                    strCamType -> {
                        val camOptions = arrayOf(
                            getString(R.string.camera_type_html),
                            getString(R.string.camera_type_port),
                            getString(R.string.camera_type_webcam)
                        )
                        showModernMenu(strCamType, camOptions) { camIndex ->
                            val typeString = when (camIndex) {
                                1 -> "port"
                                2 -> "webcam"
                                else -> "html"
                            }
                            getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).edit()
                                .putString("camera_type_$hostIp", typeString).apply()

                            val newUrl = when (typeString) {
                                "port" -> "http://$hostIp:8080/?action=stream"
                                "webcam" -> "http://$hostIp/webcam/?action=stream"
                                else -> "http://$hostIp/camera.html"
                            }
                            val currentRatio = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                .getFloat("camera_ratio_$hostIp", 56.25f)

                            loadStreamOrUrl(newUrl, currentRatio)
                        }
                    }
                    strEmergency -> {
                        val confirmDialog = AlertDialog.Builder(this@WebViewActivity)
                            .setTitle(getString(R.string.dialog_stop_title))
                            .setMessage(getString(R.string.dialog_stop_msg))
                            .setPositiveButton(getString(R.string.dialog_stop_confirm)) { _, _ ->
                                sendEmergencyStop()
                            }
                            .setNegativeButton(getString(R.string.dialog_cancel), null)
                            .create()

                        confirmDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                        confirmDialog.show()
                    }
                }
            }
        }

        btnToggle.setOnClickListener {
            val hostIp = Uri.parse(currentActiveUrl).host ?: ""
            val currentPrefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

            if (!isCameraMode) {
                val savedType = currentPrefs.getString("camera_type_$hostIp", "html") ?: "html"
                val cameraUrl = when (savedType) {
                    "port" -> "http://$hostIp:8080/?action=stream"
                    "webcam" -> "http://$hostIp/webcam/?action=stream"
                    else -> "http://$hostIp/camera.html"
                }
                val currentRatio = currentPrefs.getFloat("camera_ratio_$hostIp", 56.25f)
                loadStreamOrUrl(cameraUrl, currentRatio)
            } else {
                showModernMenu("Interface", arrayOf("Standard", "Port 4408")) { subWhich ->
                    loadStreamOrUrl(if (subWhich == 0) "http://$hostIp" else "http://$hostIp:4408", 0f)
                }
            }
        }
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        currentActiveUrl = url
        val isMjpegStream = url.contains("action=stream")
        val isHtmlCamera = url.contains("camera.html")
        isCameraMode = isMjpegStream || isHtmlCamera

        if (isCameraMode) {
            layoutOsd.visibility = if (isOsdEnabled) View.VISIBLE else View.GONE
            if (isOsdEnabled) {
                osdHandler.removeCallbacks(osdRunnable)
                osdHandler.post(osdRunnable)
            }

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
            layoutOsd.visibility = View.GONE
            osdHandler.removeCallbacks(osdRunnable)
            webView.loadUrl(url)
        }
    }

    // NEU: Hilfsfunktion zum Abspielen von System-Sounds
    private fun playSystemSound(isError: Boolean) {
        try {
            // Bei Fehler spielen wir den Alarm-Ton, sonst den normalen Benachrichtigungston
            val alertType = if (isError) RingtoneManager.TYPE_ALARM else RingtoneManager.TYPE_NOTIFICATION
            val uri = RingtoneManager.getDefaultUri(alertType)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone.play()

            // Falls es ein Fehler (Alarm) ist, stoppen wir ihn nach 3 Sekunden wieder,
            // damit der TV nicht endlos weiter klingelt.
            if (isError) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (ringtone.isPlaying) {
                        ringtone.stop()
                    }
                }, 3000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun fetchMoonrakerData() {
        val uri = Uri.parse(currentActiveUrl)
        val hostIp = uri.host ?: return
        val hostAuthority = uri.authority ?: hostIp

        val baseQuery = "printer/objects/query?extruder&heater_bed&print_stats&display_status"
        val currentChamberQuery = if (knownChamberSensor != null) "&${knownChamberSensor!!.replace(" ", "%20")}" else ""

        val urlsToTry = listOf(
            "http://$hostIp:7125/$baseQuery$currentChamberQuery",
            "http://$hostAuthority/$baseQuery$currentChamberQuery"
        )

        Thread {
            var responseText = ""
            var lastErrorMsg = ""
            var gotBadRequest = false

            for (urlStr in urlsToTry) {
                try {
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    val code = conn.responseCode
                    if (code == 200) {
                        responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        break
                    } else if (code == 400 && knownChamberSensor != null) {
                        gotBadRequest = true
                        break
                    } else {
                        lastErrorMsg = "HTTP Error: $code"
                    }
                } catch (e: Exception) {
                    lastErrorMsg = e.message ?: "Connection failed"
                }
            }

            if (gotBadRequest) {
                chamberSearchIndex++
                if (chamberSearchIndex < chamberNamesToTry.size) {
                    knownChamberSensor = chamberNamesToTry[chamberSearchIndex]
                } else {
                    knownChamberSensor = null
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

                        // NEU: Status-Logik für den Sound auslesen
                        val currentState = printStats?.optString("state", "") ?: ""

                        runOnUiThread {
                            // Sound-Trigger: Wenn wir vorher einen bekannten Status hatten und dieser sich
                            // jetzt auf 'complete' (Fertig) oder 'error' (Fehler) ändert.
                            if (lastPrintState.isNotEmpty() && lastPrintState != currentState) {
                                if (currentState == "complete") {
                                    playSystemSound(false)
                                    Toast.makeText(this@WebViewActivity, "Druck erfolgreich beendet!", Toast.LENGTH_LONG).show()
                                } else if (currentState == "error") {
                                    playSystemSound(true)
                                    Toast.makeText(this@WebViewActivity, "ACHTUNG: Klipper Fehler!", Toast.LENGTH_LONG).show()
                                }
                            }
                            // Status für die nächste Abfrage merken
                            lastPrintState = currentState

                            // --- UI Aktualisierung ---
                            findViewById<TextView>(R.id.tvOsdExtruder).text =
                                String.format("Düse: %.1f°C / %.0f°C", tempExtruder, targetExtruder)
                            findViewById<TextView>(R.id.tvOsdBed).text =
                                String.format("Bett: %.1f°C / %.0f°C", tempBed, targetBed)
                            findViewById<TextView>(R.id.tvOsdProgress).text =
                                String.format("%.1f%%", progress * 100)

                            val passedMin = duration / 60
                            val passedSec = duration % 60
                            val passedStr = String.format("%02d:%02d", passedMin, passedSec)

                            val totalStr = if (progress > 0.001) {
                                val totalTimeEstimated = (duration / progress).toInt()
                                val totMin = totalTimeEstimated / 60
                                val totSec = totalTimeEstimated % 60
                                String.format("%02d:%02d", totMin, totSec)
                            } else {
                                "--:--"
                            }
                            findViewById<TextView>(R.id.tvOsdTime).text = "Zeit: $passedStr / $totalStr"

                            val tvChamber = findViewById<TextView?>(R.id.tvOsdChamber)
                            if (tvChamber != null) {
                                if (knownChamberSensor != null) {
                                    val chamberData = status.optJSONObject(knownChamberSensor)
                                    val tempChamber = chamberData?.optDouble("temperature", 0.0) ?: 0.0
                                    val targetChamber = chamberData?.optDouble("target", 0.0) ?: 0.0

                                    if (tempChamber > 0.0) {
                                        if (targetChamber > 0.0) {
                                            tvChamber.text = String.format("Kammer: %.1f°C / %.0f°C", tempChamber, targetChamber)
                                        } else {
                                            tvChamber.text = String.format("Kammer: %.1f°C", tempChamber)
                                        }
                                        tvChamber.visibility = View.VISIBLE
                                    } else {
                                        tvChamber.visibility = View.GONE
                                    }
                                } else {
                                    tvChamber.visibility = View.GONE
                                }
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
        val hostAuthority = uri.authority ?: hostIp

        val urlsToTry = listOf(
            "http://$hostIp:7125/printer/emergency_stop",
            "http://$hostAuthority/printer/emergency_stop"
        )

        Thread {
            var success = false
            var lastResponseCode = -1

            for (urlStr in urlsToTry) {
                try {
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 3000
                    lastResponseCode = conn.responseCode

                    if (lastResponseCode in 200..299) {
                        success = true
                        break
                    }
                } catch (e: Exception) {
                    // Loop versucht Backup-Adresse
                }
            }

            runOnUiThread {
                if (success) {
                    Toast.makeText(this@WebViewActivity, getString(R.string.toast_stop_success), Toast.LENGTH_LONG).show()
                } else {
                    if (lastResponseCode != -1) {
                        Toast.makeText(this@WebViewActivity, getString(R.string.toast_stop_error) + lastResponseCode, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@WebViewActivity, getString(R.string.toast_no_connection), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun showModernMenu(title: String, items: Array<String>, onItemSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK
        val buttonBgColor = if (isNightMode) Color.parseColor("#424242") else Color.parseColor("#E0E0E0")

        val strEmergency = getString(R.string.menu_emergency_stop)

        items.forEachIndexed { index, itemText ->
            val btn = MaterialButton(this).apply {
                text = itemText
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 10, 0, 10)
                }

                if (itemText == strEmergency) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#33E53935"))
                    setTextColor(Color.parseColor("#E53935"))
                    strokeColor = ColorStateList.valueOf(Color.parseColor("#E53935"))
                    strokeWidth = 3
                } else {
                    backgroundTintList = ColorStateList.valueOf(buttonBgColor)
                    setTextColor(textColor)
                }

                cornerRadius = 24
                isFocusable = true
                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(8f).setDuration(150).start()
                        strokeWidth = 6
                        strokeColor = if (itemText == strEmergency) {
                            ColorStateList.valueOf(Color.parseColor("#FF5252"))
                        } else {
                            ColorStateList.valueOf(Color.WHITE)
                        }
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start()
                        strokeWidth = if (itemText == strEmergency) 3 else 0
                    }
                }
                setOnClickListener { onItemSelected(index); dialog.dismiss() }
            }
            container.addView(btn)
        }
        dialog.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.hasFocus()) {
            findViewById<MaterialButton>(R.id.btnWebToggle).requestFocus()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) { webView.goBack(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}