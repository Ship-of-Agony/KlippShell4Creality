package com.shipofagony.klippshell4creality

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.shipofagony.klippshell4creality.R

object NotificationManager {

    private var activeDialog: AlertDialog? = null

    /**
     * Schiebt eine unübersehbare Kachel von unten in den Bildschirm.
     */
    fun showLivePopup(context: Context, prefKey: String, titleResId: Int, messageResId: Int) {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val isDefaultEnabled = prefKey.contains("error") || prefKey.contains("100")

        val isSettingsContext = context is SettingsActivity
        val isPopupAllowed = if (isSettingsContext) true else prefs.getBoolean(prefKey, isDefaultEnabled)

        if (!isPopupAllowed) return

        dismissActivePopup()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_notification, null)
        val builder = AlertDialog.Builder(context).setView(dialogView)

        activeDialog = builder.create().apply {
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            setCanceledOnTouchOutside(false)
            window?.setWindowAnimations(R.style.NotificationDialogAnimation)
        }

        val container = dialogView.findViewById<LinearLayout>(R.id.containerNotification)
        val ivIcon = dialogView.findViewById<ImageView>(R.id.ivNotifyIcon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvNotifyTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvNotifyMessage)
        val btnDismiss = dialogView.findViewById<MaterialButton>(R.id.btnNotifyDismiss)

        tvTitle.text = context.getString(titleResId)
        tvMessage.text = context.getString(messageResId)

        // Farbliche Codierung und übersetzte Button-Texte je nach Event
        when {
            prefKey.contains("error") -> {
                container.setBackgroundColor(Color.parseColor("#B71C1C")) // Warn-Rot
                ivIcon.setImageResource(android.R.drawable.ic_delete)
                btnDismiss.text = context.getString(R.string.notify_btn_error)
            }
            prefKey.contains("offline") -> {
                container.setBackgroundColor(Color.parseColor("#F57C00")) // Warn-Gelb
                ivIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                btnDismiss.text = context.getString(R.string.notify_btn_offline)
            }
            prefKey.contains("100") -> {
                container.setBackgroundColor(Color.parseColor("#1B5E20")) // Klipper-Grün
                ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                btnDismiss.text = context.getString(R.string.notify_btn_success)
            }
            else -> {
                container.setBackgroundColor(Color.parseColor("#0D47A1")) // KlippShell-Blau
                ivIcon.setImageResource(android.R.drawable.ic_dialog_info)
                btnDismiss.text = context.getString(R.string.notify_btn_default)
            }
        }

        btnDismiss.setOnClickListener { dismissActivePopup() }

        activeDialog?.show()
        activeDialog?.window?.setGravity(Gravity.BOTTOM)
        btnDismiss.requestFocus()
    }

    fun dismissActivePopup() {
        try {
            if (activeDialog?.isShowing == true) {
                activeDialog?.dismiss()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeDialog = null
    }
}