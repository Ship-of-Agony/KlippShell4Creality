package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isNightMode) {
            window.decorView.setBackgroundColor(Color.parseColor("#121212"))
            findViewById<View>(R.id.panelSettings)?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E1E1E"))
        } else {
            window.decorView.setBackgroundColor(Color.parseColor("#F5F5F5"))
            findViewById<View>(R.id.panelSettings)?.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        }

        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        val btnThemeSelect = findViewById<MaterialButton>(R.id.btnThemeSelect)
        val btnChangeLanguage = findViewById<MaterialButton>(R.id.btnChangeLanguage)
        val btnResetApp = findViewById<MaterialButton>(R.id.btnResetApp)
        val btnSettingsBack = findViewById<MaterialButton>(R.id.btnSettingsBack)
        val ivAboutStudioLogo = findViewById<ImageView>(R.id.ivAboutStudioLogo)

        try {
            findViewById<TextView>(R.id.tvTvOptimizationLabel)?.text = getString(R.string.settings_optimized_tv)
        } catch (_: Exception) {}

        ivAboutStudioLogo.isClickable = true
        ivAboutStudioLogo.isFocusable = true

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        tvAppVersion.text = "Version ${pInfo.versionName}"

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        val savedTheme = try { prefs.getInt("theme_state", 0) } catch (e: Exception) { 0 }
        btnThemeSelect.text = when (savedTheme) {
            1 -> getString(R.string.theme_light)
            2 -> getString(R.string.theme_dark)
            else -> getString(R.string.theme_system)
        }

        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.04f).scaleY(1.04f).alpha(1.0f).translationZ(8f).setDuration(150).start()
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

        arrayOf(btnThemeSelect, btnChangeLanguage, btnResetApp, btnSettingsBack, ivAboutStudioLogo).forEach { btn ->
            btn.isFocusable = true
            btn.onFocusChangeListener = tvFocusListener
        }

        var logoClicks = 0
        ivAboutStudioLogo.setOnClickListener {
            logoClicks++
            if (logoClicks == 5) {
                showCenteredPillToast(getString(R.string.easter_egg_clicks))
            } else if (logoClicks == 7) {
                showCenteredPillToast(getString(R.string.easter_egg_success))
                it.animate().rotationBy(360f).setDuration(600).start()
                logoClicks = 0
            }
        }

        btnThemeSelect.setOnClickListener {
            val options = arrayOf(
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
            )
            showPillDialog(getString(R.string.theme_title), options) { index ->
                prefs.edit().putInt("theme_state", index).apply()

                btnThemeSelect.text = when (index) {
                    1 -> getString(R.string.theme_light)
                    2 -> getString(R.string.theme_dark)
                    else -> getString(R.string.theme_system)
                }

                when (index) {
                    1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
                recreate()
            }
        }

        btnChangeLanguage.setOnClickListener {
            val codes = resources.getStringArray(R.array.language_codes)
            val names = resources.getStringArray(R.array.language_names)
            showPillDialog("Sprache / Language", names) { index ->
                val code = codes[index]
                val localeList = if (code == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(code)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }

        btnResetApp.setOnClickListener {
            val options = arrayOf(getString(R.string.reset_app_yes), getString(R.string.reset_app_cancel))
            showPillDialog(getString(R.string.reset_app_title), options) { index ->
                if (index == 0) {
                    prefs.edit().clear().apply()
                    showCenteredPillToast("App komplett zurückgesetzt")
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    })
                    finish()
                }
            }
        }

        btnSettingsBack.setOnClickListener { finish() }
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        val pillDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f
            if (isNight) {
                setColor(Color.parseColor("#252B2E"))
                setStroke(4, Color.WHITE)
            } else {
                setColor(Color.WHITE)
                setStroke(4, Color.parseColor("#BDBDBD"))
            }
        }

        val pillView = TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = pillDrawable
            setPadding(50, 35, 50, 35)
            elevation = 12f

            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(50, 0, 50, 240)
            }
        }

        container.addView(pillView)
        rootLayout.addView(container)

        Handler(Looper.getMainLooper()).postDelayed({
            rootLayout.removeView(container)
        }, 2200)
    }

    private fun showPillDialog(title: String, items: Array<String>, onSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK
        val buttonBgColor = if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")

        val dangerWords = listOf(
            getString(R.string.reset_app_yes),
            getString(R.string.yes_delete),
            "NOT-AUS"
        )

        items.forEachIndexed { index, itemText ->
            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                cornerRadius = 100
                setPadding(0, 35, 0, 35)

                if (dangerWords.contains(itemText)) {
                    val redColorHex = if (isNightMode) "#C62828" else "#D32F2F"
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(redColorHex))
                    setTextColor(Color.WHITE)
                    strokeWidth = 0
                } else {
                    backgroundTintList = ColorStateList.valueOf(buttonBgColor)
                    setTextColor(textColor)
                }

                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 10, 0, 10)
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        strokeWidth = 6
                        strokeColor = if (dangerWords.contains(itemText)) {
                            ColorStateList.valueOf(Color.parseColor("#FF5252"))
                        } else {
                            ColorStateList.valueOf(Color.WHITE)
                        }
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start()
                        strokeWidth = 0
                    }
                }

                setOnClickListener {
                    onSelected(index)
                    dialog.dismiss()
                }
            }
            container.addView(btn)
        }
        dialog.show()
    }
}