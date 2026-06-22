package com.shipofagony.klippshell4creality

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.*
import java.util.Locale

class ScreensaverManager(
    private val layoutScreensaver: FrameLayout,
    private val ivScreensaverLogo: ImageView,
    private val defaultDrawable: Drawable?,
    private val getThumbnailBitmap: () -> Bitmap?,
    private val getLastPrintState: () -> String,
    private val getLastProgressPercent: () -> Double,
    private val getCurrentGCodeFilename: () -> String,
    private val onDeactivateRequest: () -> Unit
) {

    private var screensaverJob: Job? = null
    private var screensaverTimeoutMs: Long = 0L
    var isInstantScreensaverActive: Boolean = false
        private set

    private var tvScreensaverProgress: TextView? = null
    private val screensaverHandler = Handler(Looper.getMainLooper())
    
    private val startScreensaverRunnable = Runnable { activateScreensaver() }

    init {
        layoutScreensaver.setOnClickListener {
            if (layoutScreensaver.visibility == View.VISIBLE) {
                onDeactivateRequest()
            }
        }
    }

    fun setTimeout(timeoutMs: Long) {
        this.screensaverTimeoutMs = timeoutMs
    }

    fun getTimeout(): Long = screensaverTimeoutMs

    fun setInstantActive(active: Boolean) {
        this.isInstantScreensaverActive = active
    }

    fun resetInactivityTimer(isCameraMode: Boolean, isInPiP: Boolean) {
        screensaverHandler.removeCallbacks(startScreensaverRunnable)
        if (isCameraMode && screensaverTimeoutMs > 0L && layoutScreensaver.visibility != View.VISIBLE && !isInPiP) {
            screensaverHandler.postDelayed(startScreensaverRunnable, screensaverTimeoutMs)
        }
    }

    fun stopTimer() {
        screensaverHandler.removeCallbacks(startScreensaverRunnable)
    }

    fun isVisible(): Boolean = layoutScreensaver.visibility == View.VISIBLE

    fun activateScreensaver() {
        screensaverHandler.removeCallbacks(startScreensaverRunnable)

        layoutScreensaver.visibility = View.VISIBLE
        layoutScreensaver.bringToFront()
        layoutScreensaver.requestFocus()

        if (tvScreensaverProgress == null) {
            tvScreensaverProgress = TextView(layoutScreensaver.context).apply {
                textSize = 18f
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            }
            layoutScreensaver.addView(tvScreensaverProgress)
        }

        screensaverJob?.cancel()
        // Nutzt den Scope der aufrufenden Komponente (wird über lifecycleScope der Activity laufen)
        screensaverJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            var posX = 150f
            var posY = 150f
            var speedX = 0.4f
            var speedY = 0.3f
            
            while (isActive) {
                val sw = layoutScreensaver.width
                val sh = layoutScreensaver.height
                val lw = ivScreensaverLogo.width
                val lh = ivScreensaverLogo.height

                val thumbnailBitmap = getThumbnailBitmap()
                val lastPrintState = getLastPrintState()
                val lastProgressPercent = getLastProgressPercent()
                val currentGCodeFilename = getCurrentGCodeFilename()

                if (thumbnailBitmap != null && lastPrintState == "printing") {
                    ivScreensaverLogo.setImageBitmap(thumbnailBitmap)
                    val progPercent = String.format(Locale.getDefault(), "%.1f%%", lastProgressPercent * 100)
                    val shortName = currentGCodeFilename.substringBeforeLast(".")
                    tvScreensaverProgress?.text = "$shortName ($progPercent)"
                    tvScreensaverProgress?.visibility = View.VISIBLE
                } else {
                    defaultDrawable?.let { ivScreensaverLogo.setImageDrawable(it) }
                    tvScreensaverProgress?.visibility = View.GONE
                }

                if (sw > 0 && sh > 0 && lw > 0 && lh > 0) {
                    posX += speedX
                    posY += speedY
                    if (posX <= 0 || posX + lw >= sw) { 
                        speedX *= -1 
                        posX = posX.coerceIn(0f, (sw - lw).toFloat()) 
                    }
                    if (posY <= 0 || posY + lh >= sh) { 
                        speedY *= -1 
                        posY = posY.coerceIn(0f, (sh - lh).toFloat()) 
                    }
                    ivScreensaverLogo.x = posX
                    ivScreensaverLogo.y = posY

                    tvScreensaverProgress?.let {
                        it.x = posX - (it.width / 4)
                        it.y = posY + lh + 12
                    }
                }
                delay(32)
            }
        }
    }

    fun deactivateScreensaver(globalFallback: Long) {
        screensaverJob?.cancel()
        screensaverJob = null
        layoutScreensaver.visibility = View.GONE
        if (isInstantScreensaverActive) {
            isInstantScreensaverActive = false
            screensaverTimeoutMs = globalFallback
        }
    }

    fun destroy() {
        screensaverHandler.removeCallbacks(startScreensaverRunnable)
        screensaverJob?.cancel()
        screensaverJob = null
    }
}