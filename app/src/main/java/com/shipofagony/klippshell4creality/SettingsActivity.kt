package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.core.os.LocaleListCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // --- NEU: Der ultimative Dark Mode Fix für das Fenster ---
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isNightMode) {
            // Färbt den gesamten Hintergrund und die Karte hart auf Dark Mode um
            window.decorView.setBackgroundColor(Color.parseColor("#121212"))
            findViewById<View>(R.id.panelSettings)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
        } else {
            // Standard Light Mode
            window.decorView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            findViewById<View>(R.id.panelSettings)?.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }

        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        val switchDarkMode = findViewById<SwitchCompat>(R.id.switchDarkMode)
        val btnChangeLanguage = findViewById<Button>(R.id.btnChangeLanguage)
        val btnResetApp = findViewById<Button>(R.id.btnResetApp)
        val btnSettingsBack = findViewById<Button>(R.id.btnSettingsBack)

        val ivAboutStudioLogo = findViewById<ImageView>(R.id.ivAboutStudioLogo)
        ivAboutStudioLogo.isClickable = true
        ivAboutStudioLogo.isFocusable = true

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        tvAppVersion.text = "Version ${pInfo.versionName}"

        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).alpha(1.0f).translationZ(8f).setDuration(150).start()
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

        arrayOf(btnChangeLanguage, btnResetApp, btnSettingsBack, ivAboutStudioLogo).forEach { btn ->
            btn.isFocusable = true
            btn.alpha = 0.8f
            btn.onFocusChangeListener = tvFocusListener
        }

        // --- UNSERE EASTER EGG LOGIK (Mittig platziert & ohne das Wort Easter Egg) ---
        var logoClicks = 0
        ivAboutStudioLogo.setOnClickListener {
            logoClicks++
            when (logoClicks) {
                5 -> {
                    val toast = Toast.makeText(this, "Noch 2 Klicks...", Toast.LENGTH_SHORT)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()
                }
                7 -> {
                    // Cleaner Text, sauber in der Mitte des Bildschirms!
                    val toast = Toast.makeText(this, "Möge dein First Layer immer perfekt sein!", Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.CENTER, 0, 0)
                    toast.show()

                    it.animate().rotationBy(360f).setDuration(600).start()
                    logoClicks = 0
                }
            }
        }

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        switchDarkMode.isChecked = prefs.getBoolean("dark_mode", false)
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        }

        btnChangeLanguage.setOnClickListener {
            val codes = resources.getStringArray(R.array.language_codes)
            val names = resources.getStringArray(R.array.language_names)

            // Zwingt auch das Sprach-Auswahlfenster in den richtigen Farbmodus
            val dialogTheme = if (isNightMode) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

            AlertDialog.Builder(this, dialogTheme)
                .setTitle("Sprache / Language")
                .setItems(names) { _, which ->
                    val code = codes[which]
                    val localeList = if (code == "system") LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(code)
                    AppCompatDelegate.setApplicationLocales(localeList)
                }.show()
        }

        btnResetApp.setOnClickListener {
            prefs.edit().clear().apply()
            Toast.makeText(this, "App zurückgesetzt", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }

        btnSettingsBack.setOnClickListener { finish() }
    }
}