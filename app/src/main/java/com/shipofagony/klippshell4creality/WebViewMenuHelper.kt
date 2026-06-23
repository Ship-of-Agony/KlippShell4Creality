package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class WebViewMenuHelper(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val hostIp: String,
    private val isOsdEnabled: () -> Boolean,
    private val isThumbnailEnabled: () -> Boolean,
    private val onActionSelected: (MenuAction) -> Unit
) {

    sealed class MenuAction {
        object CompanionMenu : MenuAction()
        object DpadControl : MenuAction()
        class ToggleOsd(val newState: Boolean) : MenuAction()
        object ChangeOsdStyle : MenuAction()
        object ZoomIn : MenuAction()
        object ZoomOut : MenuAction()
        class ToggleThumbnail(val newState: Boolean) : MenuAction()
        object EnterPip : MenuAction()
        object ToggleLight : MenuAction()
        object ShowScreensaverConfig : MenuAction()
        object ChangeRatio : MenuAction()
        object ChangeCameraType : MenuAction()
        object EmergencyStop : MenuAction()
    }

    private fun toPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun getSafeString(key: String, fallback: String): String {
        return try {
            val id = context.resources.getIdentifier(key, "string", context.packageName)
            if (id != 0) context.getString(id) else fallback
        } catch (_: Exception) { fallback }
    }

    fun showMenuOptionsDialog(isCameraMode: Boolean, showPillDialog: (String, Array<String>, Array<String?>?, (Int) -> Unit) -> Unit) {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // Zentrale Sicherheitsabfrage-Funktion für NOT-AUS (erzwingt das Pill-Popup)
        val triggerEmergencyStopWithConfirmation = {
            showPillDialog(
                getSafeString("dialog_stop_title", "NOT-STOPP"),
                arrayOf(getSafeString("dialog_stop_confirm", "Ja"), getSafeString("dialog_cancel", "Nein")),
                arrayOf("#E53935", null)
            ) { choice ->
                if (choice == 0) {
                    onActionSelected(MenuAction.EmergencyStop)
                }
            }
        }

        // Falls wir nicht im Kamera-Modus sind, zeigen wir direkt das Sicherheits-Popup
        if (!isCameraMode) {
            triggerEmergencyStopWithConfirmation()
            return
        }

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getSafeString("menu_options_title", "Optionen")

        val buttonContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipToPadding = false
            clipChildren = false
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipToPadding = false
            clipChildren = false
        }
        scrollView.addView(container)
        buttonContainer?.addView(scrollView)

        val btnStyleListener = View.OnFocusChangeListener { v, hF ->
            if (hF) {
                v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                (v as MaterialButton).strokeWidth = 8
                v.strokeColor = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start()
                (v as MaterialButton).strokeWidth = 0
            }
        }

        val cardDrawable = ContextCompat.getDrawable(context, R.drawable.bg_card)

        // 1. CARD: COMPANION & DPAD
        val cardCompanion = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(toPx(12), toPx(12), toPx(12), toPx(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(6), 0, toPx(6)) }
        }

        val companionHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            weightSum = 2f
        }

        val btnCompanionMenu = MaterialButton(context).apply {
            text = getSafeString("btn_companion_menu", "Companion Menü")
            isAllCaps = false; textSize = 15f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            isFocusable = true; onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 0; rightMargin = 8 }
            setOnClickListener { dialog.dismiss(); onActionSelected(MenuAction.CompanionMenu) }
        }

        val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
        val isCompanionEnabled = (activeRole != "disabled")

        val btnDpadControl = MaterialButton(context).apply {
            text = getSafeString("btn_dpad_control", "D-Pad Steuerkreuz")
            isAllCaps = false; textSize = 15f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            if (isCompanionEnabled) {
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                setTextColor(Color.WHITE)
                isEnabled = true
            } else {
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isNight) "#33FFFFFF" else "#1A888888"))
                setTextColor(Color.parseColor(if (isNight) "#4DFFFFFF" else "#66000000"))
                isEnabled = false
            }
            isFocusable = isCompanionEnabled
            onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8; rightMargin = 0 }
            setOnClickListener { dialog.dismiss(); onActionSelected(MenuAction.DpadControl) }
        }

        companionHeaderRow.addView(btnCompanionMenu)
        companionHeaderRow.addView(btnDpadControl)
        cardCompanion.addView(companionHeaderRow)
        container.addView(cardCompanion)

        // 2. CARD: OSD CONTROL
        val cardOsd = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(toPx(12), toPx(12), toPx(12), toPx(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(6), 0, toPx(6)) }
        }

        val splitRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            weightSum = 2f
        }

        val currentOsdEnabled = isOsdEnabled()
        val btnLeftToggle = MaterialButton(context).apply {
            text = context.getString(if (currentOsdEnabled) R.string.osd_state_on else R.string.osd_state_off)
            isAllCaps = false; textSize = 15f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (currentOsdEnabled) "#4CAF50" else if (isNight) "#33FFFFFF" else "#1A888888"))
            setTextColor(if (currentOsdEnabled) Color.WHITE else if (isNight) Color.WHITE else Color.BLACK)
            isFocusable = true; onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 0; rightMargin = 8 }
            setOnClickListener { dialog.dismiss(); onActionSelected(MenuAction.ToggleOsd(!currentOsdEnabled)) }
        }

        val currentStyleName = if ((prefs.getString("osd_style_$hostIp", "box") ?: "box") == "banner") getSafeString("osd_style_banner", "Leiste") else getSafeString("osd_style_box", "Box")
        val btnRightStyle = MaterialButton(context).apply {
            text = getSafeString("menu_osd_style_title", "Stil") + ": " + currentStyleName
            isAllCaps = false; textSize = 15f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isNight) "#33FFFFFF" else "#1A888888"))
            setTextColor(if (isNight) Color.WHITE else Color.BLACK); isFocusable = true; onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8; rightMargin = 0 }
            setOnClickListener { dialog.dismiss(); onActionSelected(MenuAction.ChangeOsdStyle) }
        }

        splitRow.addView(btnLeftToggle)
        splitRow.addView(btnRightStyle)
        cardOsd.addView(splitRow)
        container.addView(cardOsd)

        // 3. CARD: ZOOM
        val cardZoom = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardDrawable
            setPadding(toPx(12), toPx(12), toPx(12), toPx(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(6), 0, toPx(12)) }
        }

        val splitRowZoom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            weightSum = 2f
        }

        val btnZoomOut = MaterialButton(context).apply {
            text = getSafeString("btn_zoom_out", "Zoom (-)")
            isAllCaps = false; textSize = 15f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isNight) "#33FFFFFF" else "#1A888888"))
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            isFocusable = true; onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 0; rightMargin = 8 }
            setOnClickListener { onActionSelected(MenuAction.ZoomOut) }
        }

        val btnZoomIn = MaterialButton(context).apply {
            text = getSafeString("btn_zoom_in", "Zoom (+)")
            isAllCaps = false; textSize = 16f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (isNight) "#33FFFFFF" else "#1A888888"))
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            isFocusable = true; onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 8; rightMargin = 0 }
            setOnClickListener { onActionSelected(MenuAction.ZoomIn) }
        }

        splitRowZoom.addView(btnZoomOut)
        splitRowZoom.addView(btnZoomIn)
        cardZoom.addView(splitRowZoom)
        container.addView(cardZoom)

        // 4. MODEL PROGRESS TOGGLE
        val currentThumbEnabled = isThumbnailEnabled()
        val btnThumbnailToggle = MaterialButton(context).apply {
            text = getSafeString("menu_thumbnail_progress", "Modell-Fortschritt")
            isAllCaps = false; textSize = 15f; cornerRadius = 100
            this.setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (currentThumbEnabled) "#4CAF50" else "#E53935"))
            setTextColor(Color.WHITE)
            isFocusable = true; onFocusChangeListener = btnStyleListener
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }
            setOnClickListener { dialog.dismiss(); onActionSelected(MenuAction.ToggleThumbnail(!currentThumbEnabled)) }
        }
        container.addView(btnThumbnailToggle)

        // 5. STANDARD LIST OPTIONS
        val menuOptions = arrayOf(
            getSafeString("menu_pip_name", "Bild in Bild (PiP)"),
            getSafeString("menu_light_control", "Beleuchtung"),
            getSafeString("menu_screensaver", "Bildschirmschoner"),
            getSafeString("menu_ratio_title", "Bildformat ändern"),
            getSafeString("menu_change_camera_type", "Live-Stream"),
            getSafeString("menu_emergency_stop", "NOT-AUS")
        )

        menuOptions.forEachIndexed { idx, optText ->
            val btn = MaterialButton(context).apply {
                text = optText; isAllCaps = false; textSize = 16f; cornerRadius = 100
                this.setPadding(0, toPx(14), 0, toPx(14))
                backgroundTintList = ColorStateList.valueOf(Color.parseColor(if (idx == 5) "#E53935" else if (isNight) "#33FFFFFF" else "#1A888888"))
                setTextColor(if (idx == 5) Color.WHITE else if (isNight) Color.WHITE else Color.BLACK)
                isFocusable = true; onFocusChangeListener = btnStyleListener
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }
                setOnClickListener {
                    dialog.dismiss()
                    when (idx) {
                        0 -> onActionSelected(MenuAction.EnterPip)
                        1 -> onActionSelected(MenuAction.ToggleLight)
                        2 -> onActionSelected(MenuAction.ShowScreensaverConfig)
                        3 -> onActionSelected(MenuAction.ChangeRatio)
                        4 -> onActionSelected(MenuAction.ChangeCameraType)
                        5 -> triggerEmergencyStopWithConfirmation() // Abfrage aufrufen
                    }
                }
            }
            container.addView(btn)
        }
        dialog.show()
    }
}