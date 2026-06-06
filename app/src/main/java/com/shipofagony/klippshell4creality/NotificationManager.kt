package com.shipofagony.klippshell4creality

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.material.button.MaterialButton
import java.lang.ref.WeakReference

object NotificationManager {

    private var activePopupViewRef: WeakReference<View>? = null

    /**
     * Schiebt eine unübersehbare Kachel von unten in den Bildschirm (Erzwungen über das Video).
     */
    fun showLivePopup(context: Context, prefKey: String, titleResId: Int, messageResId: Int) {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            return
        }

        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val isDefaultEnabled = prefKey.contains("error") || prefKey.contains("100")

        val isSettingsContext = context is SettingsActivity
        val isPopupAllowed = if (isSettingsContext) true else prefs.getBoolean(prefKey, isDefaultEnabled)

        if (!isPopupAllowed) return

        dismissActivePopup()

        try {
            val inflater = LayoutInflater.from(context)

            if (context is WebViewActivity) {
                val rootLayout = context.findViewById<ConstraintLayout>(R.id.rootLayout) ?: return
                val notifyView = inflater.inflate(R.layout.dialog_notification, rootLayout, false)
                notifyView.id = View.generateViewId()

                activePopupViewRef = WeakReference(notifyView)

                setupPopupContent(context, notifyView, prefKey, titleResId, messageResId)

                val lp = ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.WRAP_CONTENT
                )
                notifyView.layoutParams = lp
                rootLayout.addView(notifyView)

                val constraintSet = ConstraintSet()
                constraintSet.clone(rootLayout)
                constraintSet.connect(notifyView.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 40.toPx(context))
                constraintSet.connect(notifyView.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                constraintSet.connect(notifyView.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                constraintSet.applyTo(rootLayout)

                // GEFIXT: Nutzt jetzt ein garantiertes System-Asset für die Animation (Verhindert Compiler-Crash)
                val slideIn = AnimationUtils.loadAnimation(context, android.R.anim.slide_in_left)
                notifyView.startAnimation(slideIn)

                val btnDismiss = notifyView.findViewById<MaterialButton>(R.id.btnNotifyDismiss)
                btnDismiss.isFocusable = true
                btnDismiss.requestFocus()

            } else {
                val dialogView = inflater.inflate(R.layout.dialog_notification, null)
                val builder = androidx.appcompat.app.AlertDialog.Builder(context).setView(dialogView)

                val dialog = builder.create().apply {
                    window?.setBackgroundDrawableResource(android.R.color.transparent)
                    val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
                    setCanceledOnTouchOutside(!isTv)
                    window?.setWindowAnimations(R.style.NotificationDialogAnimation)
                }

                setupPopupContent(context, dialogView, prefKey, titleResId, messageResId)

                val btnDismiss = dialogView.findViewById<MaterialButton>(R.id.btnNotifyDismiss)
                btnDismiss.setOnClickListener { dialog.dismiss() }

                dialog.show()
                dialog.window?.setGravity(android.view.Gravity.BOTTOM)
                btnDismiss.isFocusable = true
                btnDismiss.requestFocus()
            }

        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Anzeigen des Live-Popups", e)
        }
    }

    /**
     * Bereitet den Inhalt, Farben und Buttons der Kachel vor.
     */
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

        if (context is WebViewActivity) {
            btnDismiss.setOnClickListener { dismissActivePopup() }
        }
    }

    /**
     * Entfernt die Kachel oder schließt den Fallback-Dialog sicher ab.
     */
    fun dismissActivePopup() {
        try {
            val view = activePopupViewRef?.get()
            if (view != null) {
                val parent = view.parent as? ViewGroup
                parent?.removeView(view)
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Entfernen der Popup-View", e)
        } finally {
            activePopupViewRef = null
        }
    }

    private fun Int.toPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}