package com.shipofagony.klippshell4creality

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import java.lang.ref.WeakReference

object NotificationManager {

    private var activeDialogRef: WeakReference<AlertDialog>? = null

    /**
     * Schiebt eine unübersehbare Kachel von unten in den Bildschirm.
     */
    fun showLivePopup(context: Context, prefKey: String, titleResId: Int, messageResId: Int) {
        // ABSICHERUNG: Wenn der Context keine valide Activity mehr ist oder beendet wird, direkt abbrechen
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
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_notification, null)
            val builder = AlertDialog.Builder(context).setView(dialogView)

            val dialog = builder.create().apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)

                val isTv = context.packageManager.hasSystemFeature("android.software.leanback")
                setCanceledOnTouchOutside(!isTv)

                window?.setWindowAnimations(R.style.NotificationDialogAnimation)
            }
            activeDialogRef = WeakReference(dialog)

            val container = dialogView.findViewById<LinearLayout>(R.id.containerNotification)
            val ivIcon = dialogView.findViewById<ImageView>(R.id.ivNotifyIcon)
            val tvTitle = dialogView.findViewById<TextView>(R.id.tvNotifyTitle)
            val tvMessage = dialogView.findViewById<TextView>(R.id.tvNotifyMessage)
            val btnDismiss = dialogView.findViewById<MaterialButton>(R.id.btnNotifyDismiss)

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

            btnDismiss.setOnClickListener { dismissActivePopup() }

            dialog.show()
            dialog.window?.setGravity(Gravity.BOTTOM)

            // FOKUS-GARANTIE für TV-Fernbedienung
            btnDismiss.isFocusable = true
            btnDismiss.requestFocus()

        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Anzeigen des Live-Popups", e)
        }
    }

    /**
     * Schließt das aktive Popup sicher ab.
     */
    fun dismissActivePopup() {
        try {
            val dialog = activeDialogRef?.get()
            if (dialog?.isShowing == true) {
                dialog.dismiss()
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Schließen des Popups", e)
        } finally {
            activeDialogRef = null
        }
    }
}