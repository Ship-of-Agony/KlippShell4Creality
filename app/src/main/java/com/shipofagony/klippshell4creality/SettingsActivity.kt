package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var panelSettings: LinearLayout
    private lateinit var panelNotifySelect: LinearLayout
    private lateinit var layoutMenuPopups: LinearLayout
    private lateinit var layoutMenuSounds: LinearLayout
    private lateinit var tvSettingsTitle: TextView

    private var currentMenuLayer = 0
    private lateinit var prefs: SharedPreferences
    private var easterEggClickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        val savedLang = prefs.getString("app_lang", "de") ?: "de"
        val locale = Locale.forLanguageTag(savedLang)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)
        baseContext.resources.configuration.updateFrom(config)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentMenuLayer > 0) {
                    handleBackNavigation()
                } else {
                    finish()
                }
            }
        })

        panelSettings = findViewById(R.id.panelSettings)
        panelNotifySelect = findViewById(R.id.panelNotifySelect)
        layoutMenuPopups = findViewById(R.id.layoutMenuPopups)
        layoutMenuSounds = findViewById(R.id.layoutMenuSounds)
        tvSettingsTitle = findViewById(R.id.tvSettingsTitle)

        val btnThemeSelect = findViewById<MaterialButton>(R.id.btnThemeSelect)
        val btnChangeLanguage = findViewById<MaterialButton>(R.id.btnChangeLanguage)
        val btnNotificationsMenu = findViewById<MaterialButton>(R.id.btnNotificationsMenu)
        val btnResetApp = findViewById<MaterialButton>(R.id.btnResetApp)
        val btnSettingsBack = findViewById<MaterialButton>(R.id.btnSettingsBack)

        val btnSubMenuSounds = findViewById<MaterialButton>(R.id.btnSubMenuSounds)
        val btnSubMenuPopups = findViewById<MaterialButton>(R.id.btnSubMenuPopups)
        val ivAboutStudioLogo = findViewById<ImageView>(R.id.ivAboutStudioLogo)

        btnThemeSelect.requestFocus()

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            findViewById<TextView>(R.id.tvAppVersion)?.text = "Version $versionName"
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tvAppVersion)?.text = "Version 1.0.5"
        }

        btnThemeSelect.setOnClickListener {
            val themes = arrayOf(getString(R.string.theme_light), getString(R.string.theme_dark), getString(R.string.theme_system))
            showTvDialog(getString(R.string.theme_title), themes) { index ->
                val targetMode = when (index) {
                    0 -> AppCompatDelegate.MODE_NIGHT_NO
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                prefs.edit().putInt("app_theme", targetMode).apply()
                AppCompatDelegate.setDefaultNightMode(targetMode)
                recreate()
            }
        }

        btnChangeLanguage.setOnClickListener {
            val languages = arrayOf("Deutsch", "English", "Español", "Français", "Čeština", "Polski", "Русский")
            val codes = arrayOf("de", "en", "es", "fr", "cs", "pl", "ru")

            showTvDialog(getString(R.string.change_language), languages) { index ->
                val selectedLang = codes[index]
                prefs.edit().putString("app_lang", selectedLang).apply()

                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
        }

        btnResetApp.setOnClickListener {
            val options = arrayOf(getString(R.string.reset_app_yes), getString(R.string.reset_app_cancel))
            val customColors = arrayOf("#E53935", null)

            showTvDialog(getString(R.string.reset_app_title), options, customColors) { index ->
                if (index == 0) {
                    prefs.edit().clear().apply()
                    initPillButtonStates()

                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                } else {
                    btnResetApp.requestFocus()
                }
            }
        }

        ivAboutStudioLogo?.setOnClickListener {
            easterEggClickCount++
            if (easterEggClickCount >= 7) {
                easterEggClickCount = 0
                ivAboutStudioLogo.animate().rotationBy(360f).setDuration(800).start()

                showTvDialog(
                    title = getString(R.string.studio_name),
                    items = arrayOf(getString(R.string.easter_egg_success)),
                    hexColors = arrayOf("#4CAF50")
                ) {
                    btnThemeSelect.requestFocus()
                }
            }
        }

        btnNotificationsMenu.setOnClickListener {
            panelSettings.visibility = View.GONE
            findViewById<View>(R.id.layoutCredits).visibility = View.GONE
            layoutMenuPopups.visibility = View.GONE
            layoutMenuSounds.visibility = View.GONE

            panelNotifySelect.visibility = View.VISIBLE
            currentMenuLayer = 1
            tvSettingsTitle.text = getString(R.string.settings_notify_title)
            btnSubMenuSounds.requestFocus()
        }

        btnSubMenuSounds.setOnClickListener {
            panelNotifySelect.visibility = View.GONE
            layoutMenuPopups.visibility = View.GONE
            layoutMenuSounds.visibility = View.VISIBLE
            currentMenuLayer = 2
            tvSettingsTitle.text = getString(R.string.submenu_sounds_title)
            findViewById<MaterialButton>(R.id.btnPillSound100).requestFocus()
        }

        btnSubMenuPopups.setOnClickListener {
            panelNotifySelect.visibility = View.GONE
            layoutMenuSounds.visibility = View.GONE
            layoutMenuPopups.visibility = View.VISIBLE
            currentMenuLayer = 2
            tvSettingsTitle.text = getString(R.string.submenu_popups_title)
            findViewById<MaterialButton>(R.id.btnPillPopup100).requestFocus()
        }

        btnSettingsBack.setOnClickListener { handleBackNavigation() }

        initPillButtonStates()
    }

    private fun handleBackNavigation() {
        when (currentMenuLayer) {
            2 -> {
                val focusTargetId = if (layoutMenuSounds.visibility == View.VISIBLE) R.id.btnSubMenuSounds else R.id.btnSubMenuPopups
                layoutMenuPopups.visibility = View.GONE
                layoutMenuSounds.visibility = View.GONE
                panelNotifySelect.visibility = View.VISIBLE
                currentMenuLayer = 1
                tvSettingsTitle.text = getString(R.string.settings_notify_title)
                findViewById<MaterialButton>(focusTargetId)?.requestFocus()
            }
            1 -> {
                panelNotifySelect.visibility = View.GONE
                panelSettings.visibility = View.VISIBLE
                findViewById<View>(R.id.layoutCredits).visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.settings_title)
                findViewById<MaterialButton>(R.id.btnNotificationsMenu).requestFocus()
            }
            0 -> finish()
        }
    }

    private fun showTvDialog(
        title: String,
        items: Array<String>,
        hexColors: Array<String?>? = null,
        onItemSelected: (Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        var firstBtn: MaterialButton? = null
        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        items.forEachIndexed { index, itemText ->
            val customHex = hexColors?.getOrNull(index)

            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                isFocusable = true

                val pillShape = ShapeAppearanceModel.builder()
                    .setAllCorners(CornerFamily.ROUNDED, 100f)
                    .build()
                this.shapeAppearanceModel = pillShape

                setPadding(0, 30, 0, 30)

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 10, 0, 10)
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 6
                        v.strokeColor = ColorStateList.valueOf(Color.WHITE)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 0
                    }
                }
                setOnClickListener { onItemSelected(index); dialog.dismiss() }
            }
            if (index == 0) firstBtn = btn
            container.addView(btn)
        }
        dialog.show()
        firstBtn?.requestFocus()
    }

    private fun initPillButtonStates() {
        updatePillVisuals(findViewById(R.id.btnThemeSelect), false)
        updatePillVisuals(findViewById(R.id.btnChangeLanguage), false)
        updatePillVisuals(findViewById(R.id.btnNotificationsMenu), false)
        updatePillVisuals(findViewById(R.id.btnSubMenuSounds), false)
        updatePillVisuals(findViewById(R.id.btnSubMenuPopups), false)

        setupPill(R.id.btnPillPopupFirstLayer, "popup_first_layer", false)
        setupPill(R.id.btnPillPopup50, "popup_50", false)
        setupPill(R.id.btnPillPopup75, "popup_75", false)
        setupPill(R.id.btnPillPopup90, "popup_90", false)
        setupPill(R.id.btnPillPopup100, "popup_100", true)
        setupPill(R.id.btnPillPopupOffline, "popup_offline", false)
        setupPill(R.id.btnPillPopupError, "popup_error", true)

        setupPill(R.id.btnPillSoundFirstLayer, "sound_first_layer", false)
        setupPill(R.id.btnPillSound50, "sound_50", false)
        setupPill(R.id.btnPillSound75, "sound_75", false)
        setupPill(R.id.btnPillSound90, "sound_90", false)
        setupPill(R.id.btnPillSound100, "sound_100", true)
        setupPill(R.id.btnPillSoundOffline, "sound_offline", false)
        setupPill(R.id.btnPillSoundError, "sound_error", true)
    }

    private fun setupPill(buttonId: Int, prefKey: String, defaultValue: Boolean) {
        val btn = findViewById<MaterialButton>(buttonId) ?: return

        val pillShape = ShapeAppearanceModel.builder()
            .setAllCorners(CornerFamily.ROUNDED, 100f)
            .build()
        btn.shapeAppearanceModel = pillShape

        var isActive = prefs.getBoolean(prefKey, defaultValue)
        updatePillVisuals(btn, isActive)

        btn.setOnClickListener {
            isActive = !isActive
            prefs.edit().putBoolean(prefKey, isActive).apply()
            updatePillVisuals(btn, isActive)

            if (isActive) {
                if (prefKey.startsWith("sound_")) {
                    SoundManager.playPreview(prefKey)
                } else if (prefKey.startsWith("popup_")) {
                    triggerPopupPreview(prefKey)
                }
            } else {
                if (prefKey.startsWith("sound_")) {
                    SoundManager.stopAllSounds()
                } else if (prefKey.startsWith("popup_")) {
                    NotificationManager.dismissActivePopup()
                }
            }
        }
    }

    private fun triggerPopupPreview(prefKey: String) {
        val titleId: Int
        val msgId: Int

        when {
            prefKey.contains("error") -> { titleId = R.string.notify_title_error; msgId = R.string.notify_msg_error }
            prefKey.contains("offline") -> { titleId = R.string.notify_title_offline; msgId = R.string.notify_msg_offline }
            prefKey.contains("100") -> { titleId = R.string.notify_title_100; msgId = R.string.notify_msg_100 }
            prefKey.contains("first_layer") -> { titleId = R.string.notify_title_first_layer; msgId = R.string.notify_msg_first_layer }
            prefKey.contains("50") -> { titleId = R.string.notify_title_50; msgId = R.string.notify_msg_50 }
            prefKey.contains("75") -> { titleId = R.string.notify_title_75; msgId = R.string.notify_msg_75 }
            else -> { titleId = R.string.notify_title_90; msgId = R.string.notify_msg_90 }
        }

        NotificationManager.showLivePopup(this, prefKey, titleId, msgId)
    }

    private fun updatePillVisuals(btn: MaterialButton?, isActive: Boolean) {
        if (btn == null) return
        val isErrorPill = btn.id == R.id.btnPillPopupError || btn.id == R.id.btnPillSoundError

        if (isActive) {
            if (isErrorPill) {
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            }
            btn.setTextColor(Color.WHITE)
        } else {
            if (isErrorPill) {
                val errorBgColor = ContextCompat.getColor(this, R.color.pill_error_inactive)
                val errorTxtColor = ContextCompat.getColor(this, R.color.pill_error_inactive_text)
                btn.backgroundTintList = ColorStateList.valueOf(errorBgColor)
                btn.setTextColor(errorTxtColor)
            } else {
                val normalBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
                val normalTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)
                btn.backgroundTintList = ColorStateList.valueOf(normalBgColor)
                btn.setTextColor(normalTxtColor)
            }
        }
    }

    override fun onPause() {
        SoundManager.stopAllSounds()
        NotificationManager.dismissActivePopup()
        super.onPause()
    }

    override fun onDestroy() {
        SoundManager.stopAllSounds()
        NotificationManager.dismissActivePopup()
        super.onDestroy()
    }
}