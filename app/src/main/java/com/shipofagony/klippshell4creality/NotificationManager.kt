package com.shipofagony.klippshell4creality

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.lang.ref.WeakReference

object NotificationManager {

    private var activePopupViewRef: WeakReference<View>? = null
    private var activeContainerRef: WeakReference<ViewGroup>? = null

    // Speicher für die Rückkehr-Aktion in die Einstellungen
    private var currentDismissCallback: (() -> Unit)? = null

    /**
     * Schiebt eine unübersehbare Kachel in den Bildschirm (Erzwungen über das native Layout).
     * Unterstützt jetzt ein optionales Lambda-Callback beim Schließen.
     */
    fun showLivePopup(context: Context, prefKey: String, titleResId: Int, messageResId: Int, onDismiss: (() -> Unit)? = null) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }

        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val isDefaultEnabled = true

        val isSettingsContext = context is SettingsActivity
        val isPopupAllowed = if (isSettingsContext) true else prefs.getBoolean(prefKey, isDefaultEnabled)

        if (!isPopupAllowed) return

        // Schließt ein eventuell altes Popup und löscht dessen Callback, ohne es abzufeuern
        dismissActivePopup()

        // Sichert das neue Callback für den Schließen-Button
        this.currentDismissCallback = onDismiss

        try {
            val inflater = LayoutInflater.from(context)

            if (context is WebViewActivity) {
                val webContainer = context.findViewById<ViewGroup>(R.id.containerWebNotification)

                if (webContainer != null) {
                    val notifyView = inflater.inflate(R.layout.dialog_notification, webContainer, false)
                    activePopupViewRef = WeakReference(notifyView)
                    activeContainerRef = WeakReference(webContainer)

                    setupPopupContent(context, notifyView, prefKey, titleResId, messageResId)

                    webContainer.removeAllViews()
                    webContainer.addView(notifyView)

                    notifyView.bringToFront()
                    notifyView.elevation = 50.toPx(context).toFloat()
                    notifyView.translationZ = 50.toPx(context).toFloat()

                    webContainer.visibility = View.VISIBLE

                    val slideIn = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left)
                    notifyView.startAnimation(slideIn)

                    val btnDismiss = notifyView.findViewById<MaterialButton>(R.id.btnNotifyDismiss)
                    btnDismiss.isFocusable = true
                    btnDismiss.requestFocus()
                    return
                }
            }

            val activity = context as? Activity
            val rootLayout = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)

            if (rootLayout != null) {
                val notifyView = inflater.inflate(R.layout.dialog_notification, rootLayout, false)
                activePopupViewRef = WeakReference(notifyView)
                activeContainerRef = null

                setupPopupContent(context, notifyView, prefKey, titleResId, messageResId)

                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(20.toPx(context), 0, 20.toPx(context), 40.toPx(context))
                }
                notifyView.layoutParams = lp
                rootLayout.addView(notifyView)

                notifyView.bringToFront()
                notifyView.elevation = 50.toPx(context).toFloat()
                notifyView.translationZ = 50.toPx(context).toFloat()

                val slideIn = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left)
                notifyView.startAnimation(slideIn)

                val btnDismiss = notifyView.findViewById<MaterialButton>(R.id.btnNotifyDismiss)
                btnDismiss.isFocusable = true
                btnDismiss.requestFocus()
            }

        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Anzeigen des Live-Popups", e)
        }
    }

    private fun setupPopupContent(context: Context, view: View, prefKey: String, titleResId: Int, messageResId: Int) {
        val container = view.findViewById<LinearLayout>(R.id.containerNotification)
        val ivIcon = view.findViewById<ImageView>(R.id.ivNotifyIcon)
        val tvTitle = view.findViewById<TextView>(R.id.tvNotifyTitle)
        val tvMessage = view.findViewById<TextView>(R.id.tvNotifyMessage)
        val btnDismiss = view.findViewById<MaterialButton>(R.id.btnNotifyDismiss)

        tvTitle.text = context.getString(titleResId)
        tvMessage.text = context.getString(messageResId)

        when {
            prefKey.contains("error") -> {
                container.setBackgroundColor(Color.parseColor("#B71C1C"))
                ivIcon.setImageResource(android.R.drawable.ic_delete)
                btnDismiss.text = context.getString(R.string.notify_btn_error)
            }
            prefKey.contains("offline") -> {
                container.setBackgroundColor(Color.parseColor("#F57C00"))
                ivIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                btnDismiss.text = context.getString(R.string.notify_btn_offline)
            }
            prefKey.contains("100") -> {
                container.setBackgroundColor(Color.parseColor("#1B5E20"))
                ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                btnDismiss.text = context.getString(R.string.notify_btn_success)
            }
            else -> {
                container.setBackgroundColor(Color.parseColor("#0D47A1"))
                ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                btnDismiss.text = context.getString(R.string.notify_btn_default)
            }
        }

        btnDismiss.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
        btnDismiss.setTextColor(Color.WHITE)
        btnDismiss.cornerRadius = 100

        // TIMING-INVERSION: Sichert die Aktion, räumt die View ab und triggert erst dann die Rückkehr!
        btnDismiss.setOnClickListener {
            val callback = currentDismissCallback
            dismissActivePopup()
            callback?.invoke()
        }
    }

    fun dismissActivePopup() {
        try {
            val webContainer = activeContainerRef?.get()
            if (webContainer != null) {
                webContainer.visibility = View.GONE
                webContainer.removeAllViews()
            } else {
                val view = activePopupViewRef?.get()
                if (view != null) {
                    val parent = view.parent as? ViewGroup
                    parent?.removeView(view)
                }
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Entfernen der Popup-View", e)
        } finally {
            activePopupViewRef = null
            activeContainerRef = null
            currentDismissCallback = null // Setzt den Schalter verlässlich zurück
        }
    }

    private fun Int.toPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}