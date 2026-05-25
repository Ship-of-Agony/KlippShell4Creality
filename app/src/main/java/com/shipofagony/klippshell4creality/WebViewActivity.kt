package com.shipofagony.klippshell4creality

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var currentActiveUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview)

        webView = findViewById(R.id.webView)
        val btnBack = findViewById<Button>(R.id.btnWebBack)
        val btnToggle = findViewById<Button>(R.id.btnWebToggle)
        val btnClose = findViewById<Button>(R.id.btnWebClose)
        val btnWebRatio = findViewById<Button>(R.id.btnWebRatio)

        // TV-Box Fernbedienungs-Fokus (Leuchtrand + Schatten)
        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.1f).scaleY(1.1f).alpha(1.0f).translationZ(8f).setDuration(150).start()
                if (view is com.google.android.material.button.MaterialButton) {
                    view.strokeWidth = 6
                    view.strokeColor = android.content.res.ColorStateList.valueOf(Color.WHITE)
                }
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.8f).translationZ(0f).setDuration(150).start()
                if (view is com.google.android.material.button.MaterialButton) {
                    view.strokeWidth = 0
                }
            }
        }

        arrayOf(btnBack, btnToggle, btnClose, btnWebRatio).forEach { btn ->
            btn.isFocusable = true
            btn.alpha = 0.8f
            btn.onFocusChangeListener = tvFocusListener
        }

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()

        // DAUERHAFT SICHTBARER SCROLLBALKEN
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
        loadStreamOrUrl(currentActiveUrl, 56.25f)

        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() else finish() }
        btnClose.setOnClickListener { finish() }

        btnWebRatio.setOnClickListener {
            showModernMenu("Format wählen", arrayOf("1:1", "16:9", "4:3")) { which ->
                val ratio = when (which) {
                    0 -> 100f
                    1 -> 56.25f
                    else -> 75f
                }
                loadStreamOrUrl(currentActiveUrl, ratio)
            }
        }

        btnToggle.setOnClickListener {
            val hostIp = Uri.parse(currentActiveUrl).host ?: ""

            showModernMenu("Ansicht wechseln", arrayOf("Interface", "Kamera")) { mainWhich ->
                if (mainWhich == 0) {
                    showModernMenu("Interface wählen", arrayOf("Standard", "Port 4408")) { subWhich ->
                        loadStreamOrUrl(if (subWhich == 0) "http://$hostIp" else "http://$hostIp:4408", 0f)
                    }
                } else {
                    showModernMenu("Kamera wählen", arrayOf("über HTML", "über Port", "über Webcam")) { subWhich ->
                        val url = when (subWhich) {
                            0 -> "http://$hostIp/camera.html"
                            1 -> "http://$hostIp:8080/?action=stream"
                            else -> "http://$hostIp/webcam/?action=stream"
                        }
                        loadStreamOrUrl(url, 56.25f)
                    }
                }
            }
        }
    }

    private fun loadStreamOrUrl(url: String, paddingTopPercent: Float) {
        currentActiveUrl = url
        val btnWebRatio = findViewById<Button>(R.id.btnWebRatio)

        val isMjpegStream = url.contains("action=stream")
        val isHtmlCamera = url.contains("camera.html")
        val isCamera = isMjpegStream || isHtmlCamera

        if (isCamera) {
            // Kamera-Modus: Button voll aktivieren und einblenden
            btnWebRatio.visibility = View.VISIBLE
            btnWebRatio.isFocusable = true

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
                        .container {
                            position: relative;
                            width: 100%;
                            height: 0;
                            padding-top: ${paddingTopPercent}%;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        $mediaElement
                    </div>
                </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
        } else {
            // Klipper-Interface: Button komplett verschwinden lassen und Fokus sperren
            btnWebRatio.visibility = View.GONE
            btnWebRatio.isFocusable = false
            webView.loadUrl(url)
        }
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

        items.forEachIndexed { index, itemText ->
            val btn = com.google.android.material.button.MaterialButton(this).apply {
                text = itemText
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 10, 0, 10)
                }
                backgroundTintList = ColorStateList.valueOf(buttonBgColor)
                setTextColor(textColor)
                cornerRadius = 24

                isFocusable = true
                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.05f).scaleY(1.05f).translationZ(8f).setDuration(150).start()
                        strokeWidth = 6
                        strokeColor = android.content.res.ColorStateList.valueOf(Color.WHITE)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start()
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
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.hasFocus()) {
            val btnToggle = findViewById<Button>(R.id.btnWebToggle)
            btnToggle.requestFocus()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }
}