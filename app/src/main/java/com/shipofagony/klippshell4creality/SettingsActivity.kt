package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var panelSettings: LinearLayout
    private lateinit var panelTheme: LinearLayout
    private lateinit var panelLanguage: LinearLayout
    private lateinit var panelNotifySelect: LinearLayout
    private lateinit var panelScreensaver: LinearLayout
    private lateinit var panelAbout: LinearLayout
    private lateinit var containerLanguageButtons: LinearLayout

    private lateinit var tvSettingsTitle: TextView
    private lateinit var tvChangelogContent: TextView
    private lateinit var scrollChangelogBox: ScrollView

    private lateinit var tvLicensesLink: TextView
    private lateinit var tvAboutContactLink: TextView

    private var currentMenuLayer = 0
    private lateinit var prefs: SharedPreferences
    private var easterEggClickCount = 0

    private var isDualScreenMode = false

    private var advancedHeaderView: TextView? = null
    private var advancedTvButton: MaterialButton? = null

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

        isDualScreenMode = findViewById<View>(R.id.guidelineCenter) != null

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
        panelTheme = findViewById(R.id.panelTheme)
        panelLanguage = findViewById(R.id.panelLanguage)
        panelNotifySelect = findViewById(R.id.panelNotifySelect)
        panelScreensaver = findViewById(R.id.panelScreensaver)
        panelAbout = findViewById(R.id.panelAbout)
        containerLanguageButtons = findViewById(R.id.containerLanguageButtons)

        tvSettingsTitle = findViewById(R.id.tvSettingsTitle)
        tvChangelogContent = findViewById(R.id.tvChangelogContent)
        scrollChangelogBox = findViewById(R.id.scrollChangelogBox)

        tvLicensesLink = findViewById(R.id.tvLicensesLink)

        tvAboutContactLink = findViewById(R.id.tvAboutContactLink)
        tvAboutContactLink.text = getString(R.string.about_contact_text)
        tvAboutContactLink.setTextColor(Color.parseColor("#2196F3"))

        val btnThemeSelect = findViewById<MaterialButton>(R.id.btnThemeSelect)
        val btnChangeLanguage = findViewById<MaterialButton>(R.id.btnChangeLanguage)
        val btnGlobalScreensaver = findViewById<MaterialButton>(R.id.btnGlobalScreensaver)
        val btnNotificationsMenu = findViewById<MaterialButton>(R.id.btnNotificationsMenu)
        val btnAboutMenu = findViewById<MaterialButton>(R.id.btnAboutMenu)
        val btnResetApp = findViewById<MaterialButton>(R.id.btnResetApp)
        val btnSettingsBack = findViewById<MaterialButton>(R.id.btnSettingsBack)

        val btnPillThemeLight = findViewById<MaterialButton>(R.id.btnPillThemeLight)
        val btnPillThemeDark = findViewById<MaterialButton>(R.id.btnPillThemeDark)
        val btnPillThemeSystem = findViewById<MaterialButton>(R.id.btnPillThemeSystem)

        val btnPillThemeSounds = findViewById<MaterialButton>(R.id.btnSubMenuSounds)
        val btnPillThemePopups = findViewById<MaterialButton>(R.id.btnSubMenuPopups)

        val btnCheckUpdates = findViewById<MaterialButton>(R.id.btnCheckUpdates)
        val ivAboutStudioLogo = findViewById<ImageView>(R.id.ivAboutStudioLogo)

        btnThemeSelect.requestFocus()

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            findViewById<TextView>(R.id.tvAppVersion)?.text = "Version $versionName"
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tvAppVersion)?.text = "Version 0.8.5.050626-rc"
        }

        btnThemeSelect.setOnClickListener {
            if (!isDualScreenMode) panelSettings.visibility = View.GONE
            hideAllSubPanelsExcept(panelTheme)

            panelTheme.visibility = View.VISIBLE
            currentMenuLayer = 5
            tvSettingsTitle.text = getString(R.string.theme_title)

            val currentMode = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            updateSubpagePillColor(btnPillThemeLight, currentMode == AppCompatDelegate.MODE_NIGHT_NO)
            updateSubpagePillColor(btnPillThemeDark, currentMode == AppCompatDelegate.MODE_NIGHT_YES)
            updateSubpagePillColor(btnPillThemeSystem, currentMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

            when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> btnPillThemeSystem.requestFocus()
                AppCompatDelegate.MODE_NIGHT_NO -> btnPillThemeLight.requestFocus()
                else -> btnPillThemeDark.requestFocus()
            }
        }

        val themeClick = View.OnClickListener { view ->
            val targetMode = when (view.id) {
                R.id.btnPillThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnPillThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("app_theme", targetMode).apply()
            AppCompatDelegate.setDefaultNightMode(targetMode)
            recreate()
        }
        btnPillThemeLight.setOnClickListener(themeClick)
        btnPillThemeDark.setOnClickListener(themeClick)
        btnPillThemeSystem.setOnClickListener(themeClick)

        btnChangeLanguage.setOnClickListener {
            if (!isDualScreenMode) panelSettings.visibility = View.GONE
            hideAllSubPanelsExcept(panelLanguage)

            panelLanguage.visibility = View.VISIBLE
            currentMenuLayer = 6
            tvSettingsTitle.text = getString(R.string.change_language)
            buildDynamicLanguageMenu()
        }

        btnGlobalScreensaver.setOnClickListener {
            if (!isDualScreenMode) panelSettings.visibility = View.GONE
            hideAllSubPanelsExcept(panelScreensaver)

            panelScreensaver.visibility = View.VISIBLE
            currentMenuLayer = 4
            tvSettingsTitle.text = getString(R.string.menu_screensaver)
            refreshScreensaverSubpagePills()
            findViewById<MaterialButton>(R.id.btnPillSaver30).requestFocus()
        }

        btnNotificationsMenu.setOnClickListener {
            if (!isDualScreenMode) panelSettings.visibility = View.GONE
            hideAllSubPanelsExcept(panelNotifySelect)

            panelNotifySelect.visibility = View.VISIBLE
            currentMenuLayer = 1
            tvSettingsTitle.text = getString(R.string.settings_notify_title)
            btnPillThemeSounds.requestFocus()
        }

        btnPillThemeSounds.setOnClickListener {
            showSoundsConfigurationDialog()
        }

        btnPillThemePopups.setOnClickListener {
            showPopupsConfigurationDialog()
        }

        btnAboutMenu.setOnClickListener {
            if (!isDualScreenMode) panelSettings.visibility = View.GONE
            hideAllSubPanelsExcept(panelAbout)

            panelAbout.visibility = View.VISIBLE
            currentMenuLayer = 3
            tvSettingsTitle.text = getString(R.string.btn_about_menu)
            loadChangelogFromAssets()
            btnCheckUpdates.requestFocus()
        }

        btnResetApp.setOnClickListener {
            val options = arrayOf(getString(R.string.reset_app_yes), getString(R.string.reset_app_cancel))
            val resetColors = arrayOf("#E53935", null)

            showTvDialog(getString(R.string.reset_app_title), options, resetColors) { index ->
                if (index == 0) {
                    prefs.edit().clear().apply()
                    removeAdvancedMenuViews()
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

                prefs.edit().putBoolean("is_advanced_mode", true).apply()
                checkAndRenderAdvancedMenu()

                showTvDialog(getString(R.string.studio_name), arrayOf(getString(R.string.easter_egg_success)), arrayOf("#4CAF50")) {}
            }
        }

        btnCheckUpdates.setOnClickListener {
            showUpdateSubMenuDialog()
        }

        tvLicensesLink.setOnClickListener {
            showLicensesDialog()
        }
        tvLicensesLink.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(150).start()
            (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#2196F3"))
        }

        tvAboutContactLink.setOnClickListener {
            val contactOptions = arrayOf("GitHub Repository", "klippshell@gmail.com")
            showTvDialog(getString(R.string.studio_name), contactOptions, arrayOf("#4CAF50", "#2196F3")) { choice ->
                if (choice == 0) {
                    val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ship-of-Agony/KlippShell4Creality"))
                    startActivity(urlIntent)
                } else {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("klippshell@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "KlippShell Support Request")
                    }
                    try {
                        startActivity(Intent.createChooser(emailIntent, "Send Mail..."))
                    } catch (e: Exception) {
                        showCenteredPillToast(getString(R.string.toast_email_error))
                    }
                }
            }
        }
        tvAboutContactLink.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(150).start()
            (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#2196F3"))
        }

        setupSaverPagePill(R.id.btnPillSaver30, 30 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaver60, 60 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaver90, 90 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaver120, 120 * 60 * 1000L)
        // GEFIXT (Zeile 300): Übergibt jetzt sauber 0L als Timeout-Parameter
        setupSaverPagePill(R.id.btnPillSaverOff, 0L)

        btnSettingsBack.setOnClickListener { handleBackNavigation() }

        scrollChangelogBox.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.02f else 1.0f).scaleY(if (hasFocus) 1.02f else 1.0f).setDuration(150).start()
            val bgDrawable = v.background as? GradientDrawable
            if (bgDrawable != null) {
                if (hasFocus) {
                    bgDrawable.setStroke(6, Color.WHITE)
                } else {
                    val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    bgDrawable.setStroke(3, Color.parseColor(if (isNight) "#40FFFFFF" else "#33000000"))
                }
            }
        }

        arrayOf(
            btnThemeSelect, btnChangeLanguage, btnGlobalScreensaver, btnNotificationsMenu,
            btnAboutMenu, btnResetApp, btnPillThemeSounds, btnPillThemePopups,
            btnPillThemeLight, btnPillThemeDark, btnPillThemeSystem
        ).forEach { btn ->
            btn.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }
        }

        initPillButtonStates()
        checkAndRenderAdvancedMenu()

        if (isDualScreenMode) {
            hideAllSubPanelsExcept(panelTheme)
            panelTheme.visibility = View.VISIBLE
            currentMenuLayer = 5
            tvSettingsTitle.text = getString(R.string.theme_title)

            val currentMode = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            updateSubpagePillColor(btnPillThemeLight, currentMode == AppCompatDelegate.MODE_NIGHT_NO)
            updateSubpagePillColor(btnPillThemeDark, currentMode == AppCompatDelegate.MODE_NIGHT_YES)
            updateSubpagePillColor(btnPillThemeSystem, currentMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun updateSubpagePillColor(btn: MaterialButton?, isSelected: Boolean) {
        if (btn == null) return
        btn.backgroundTintList = ColorStateList.valueOf(if (isSelected) Color.parseColor("#4CAF50") else ContextCompat.getColor(this, R.color.pill_normal_inactive))
        btn.setTextColor(if (isSelected) Color.WHITE else ContextCompat.getColor(this, R.color.pill_normal_inactive_text))

        if (!btn.isFocused) {
            btn.strokeWidth = 0
            btn.strokeColor = null
        } else {
            btn.strokeWidth = 8
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            btn.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkAndRenderAdvancedMenu() {
        val isAdvancedActive = prefs.getBoolean("is_advanced_mode", false)
        if (!isAdvancedActive) return

        if (advancedHeaderView != null || advancedTvButton != null) return

        val mainContainer = findViewById<LinearLayout>(R.id.panelSettings) ?: return
        val btnResetApp = findViewById<MaterialButton>(R.id.btnResetApp) ?: return
        val indexReset = mainContainer.indexOfChild(btnResetApp)

        if (indexReset == -1) return

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textLabelColor = if (isNight) Color.parseColor("#80FFFFFF") else Color.parseColor("#80000000")

        advancedHeaderView = TextView(this).apply {
            text = "Advanced"
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(textLabelColor)
            setPadding(0, toPx(24), 0, toPx(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        advancedTvButton = MaterialButton(this).apply {
            text = getString(R.string.btn_advanced_trigger_tv)
            isAllCaps = false
            textSize = 16f
            isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, toPx(14), 0, toPx(14))
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@SettingsActivity, R.color.pill_normal_inactive))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.pill_normal_inactive_text))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, toPx(8), 0, toPx(8))
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }
            setOnClickListener {
                try {
                    val workClass = Class.forName("com.shipofagony.klippshell4creality.KlipperTvWorker") as Class<out androidx.work.ListenableWorker>
                    WorkManager.getInstance(applicationContext).enqueue(OneTimeWorkRequest.Builder(workClass).build())
                    showCenteredPillToast(getString(R.string.btn_advanced_trigger_tv) + " ✓")
                } catch (e: Exception) {
                    Log.e("KlippShell", "Advanced TV-Worker Trigger failed", e)
                }
            }
        }

        mainContainer.addView(advancedHeaderView, indexReset)
        mainContainer.addView(advancedTvButton, indexReset + 1)
    }

    private fun removeAdvancedMenuViews() {
        val mainContainer = findViewById<LinearLayout>(R.id.panelSettings) ?: return
        advancedHeaderView?.let { mainContainer.removeView(it); advancedHeaderView = null }
        advancedTvButton?.let { mainContainer.removeView(it); advancedTvButton = null }
    }

    private fun showUpdateSubMenuDialog() {
        val isAutoCheckActive = prefs.getBoolean("update_auto_check", true)
        val options = arrayOf(getString(R.string.btn_manual_search), if (isAutoCheckActive) getString(R.string.pill_auto_check_on) else getString(R.string.pill_auto_check_off))
        val hexColors = arrayOf(null, if (isAutoCheckActive) "#4CAF50" else "#E53935")

        showTvDialog(getString(R.string.submenu_update_title), options, hexColors) { index ->
            if (index == 0) {
                showCenteredPillToast(getString(R.string.toast_updater_checking))
                checkForUpdatesFromGithub()
            } else {
                prefs.edit().putBoolean("update_auto_check", !isAutoCheckActive).apply()
                showUpdateSubMenuDialog()
            }
        }
    }

    private fun showSoundsConfigurationDialog() {
        val soundKeys = arrayOf(
            "sound_offline", "sound_first_layer",
            "sound_50", "sound_75",
            "sound_90", "sound_100"
        )
        val soundTitles = arrayOf(
            getString(R.string.notify_title_offline), getString(R.string.notify_title_first_layer),
            getString(R.string.notify_title_50), getString(R.string.notify_title_75),
            getString(R.string.notify_title_90), getString(R.string.notify_title_100)
        )

        val options = Array(soundKeys.size) { i ->
            val isEnabled = prefs.getBoolean(soundKeys[i], true)
            soundTitles[i] + if (isEnabled) " ✓" else ""
        }

        val hexColors = Array<String?>(soundKeys.size) { i ->
            if (prefs.getBoolean(soundKeys[i], true)) "#4CAF50" else null
        }

        showTvDialog(getString(R.string.submenu_sounds_title), options, hexColors) { index ->
            val targetKey = soundKeys[index]
            val currentVal = prefs.getBoolean(targetKey, true)
            val newVal = !currentVal
            prefs.edit().putBoolean(targetKey, newVal).apply()

            if (newVal) {
                this@SettingsActivity.showCenteredPillToast("🔊 Ton aktiviert: " + soundTitles[index] + " ✓")
                try {
                    SoundManager.playLiveNotification(targetKey)
                } catch (e: Exception) {
                    Log.e("KlippShell", "Direkter SoundManager Aufruf blockiert", e)
                }
            }

            showSoundsConfigurationDialog()
        }
    }

    private fun showPopupsConfigurationDialog() {
        val popupKeys = arrayOf(
            "popup_offline", "popup_first_layer",
            "popup_50", "popup_75",
            "popup_90", "popup_100"
        )
        val popupTitles = arrayOf(
            getString(R.string.notify_title_offline), getString(R.string.notify_title_first_layer),
            getString(R.string.notify_title_50), getString(R.string.notify_title_75),
            getString(R.string.notify_title_90), getString(R.string.notify_title_100)
        )

        val popupMessages = arrayOf(
            R.string.notify_msg_offline, R.string.notify_msg_first_layer,
            R.string.notify_msg_50, R.string.notify_msg_75,
            R.string.notify_msg_90, R.string.notify_msg_100
        )
        val popupTitleIds = arrayOf(
            R.string.notify_title_offline, R.string.notify_title_first_layer,
            R.string.notify_title_50, R.string.notify_title_75,
            R.string.notify_title_90, R.string.notify_title_100
        )

        val options = Array(popupKeys.size) { i ->
            val isEnabled = prefs.getBoolean(popupKeys[i], true)
            popupTitles[i] + if (isEnabled) " ✓" else ""
        }

        val hexColors = Array<String?>(popupKeys.size) { i ->
            if (prefs.getBoolean(popupKeys[i], true)) "#4CAF50" else null
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = getString(R.string.submenu_popups_title)
        val oldContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val dialogScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(210)).apply {
                setMargins(0, toPx(4), 0, toPx(4))
            }
            isVerticalScrollBarEnabled = true
        }

        val scrollContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dialogScrollView.addView(scrollContainer)

        var firstBtn: MaterialButton? = null
        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        options.forEachIndexed { index, itemText ->
            val customHex = hexColors.getOrNull(index)
            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, toPx(30), 0, toPx(30))

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, toPx(8), 0, toPx(8))
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 0
                    }
                }

                setOnClickListener {
                    val targetKey = popupKeys[index]
                    val currentVal = prefs.getBoolean(targetKey, true)
                    val newVal = !currentVal
                    prefs.edit().putBoolean(targetKey, newVal).apply()

                    dialog.dismiss()

                    if (newVal) {
                        // Nutzt das Trailing-Lambda des modifizierten NotificationManagers
                        NotificationManager.showLivePopup(
                            this@SettingsActivity,
                            targetKey,
                            popupTitleIds[index],
                            popupMessages[index]
                        ) {
                            showPopupsConfigurationDialog()
                        }
                    } else {
                        showPopupsConfigurationDialog()
                    }
                }
            }
            if (index == 0) firstBtn = btn
            scrollContainer.addView(btn)
        }

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default)
            isAllCaps = false
            textSize = 16f
            isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, toPx(26), 0, toPx(26))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, toPx(16), 0, toPx(8))
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 0
                }
            }
            setOnClickListener {
                dialog.dismiss()
                if (isDualScreenMode) handleBackNavigation() else initPillButtonStates()
            }
        }
        scrollContainer.addView(closeBtn)

        oldContainer?.parent?.let { parent ->
            val group = parent as ViewGroup
            val idx = group.indexOfChild(oldContainer)
            if (idx != -1) {
                group.removeViewAt(idx)
                group.addView(dialogScrollView, idx)
            }
        }

        dialog.setOnCancelListener {
            if (isDualScreenMode) handleBackNavigation() else initPillButtonStates()
        }

        dialog.show()
        firstBtn?.requestFocus()
    }

    private fun buildDynamicLanguageMenu() {
        containerLanguageButtons.removeAllViews()
        val names = resources.getStringArray(R.array.language_names)
        val codes = resources.getStringArray(R.array.language_codes)
        val activeCode = prefs.getString("app_lang", "system") ?: "system"

        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)
        var preFocused: MaterialButton? = null

        names.forEachIndexed { index, langName ->
            val isSelected = codes[index] == activeCode
            val btn = MaterialButton(this).apply {
                text = langName
                isAllCaps = false
                textSize = 16f
                isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, toPx(30), 0, toPx(30))

                if (isSelected) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, toPx(8), 0, toPx(8))
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                        v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 0
                    }
                }

                setOnClickListener {
                    prefs.edit().putString("app_lang", codes[index]).apply()
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                    finish()
                }
            }
            if (isSelected) preFocused = btn
            containerLanguageButtons.addView(btn)
        }
        (preFocused ?: containerLanguageButtons.getChildAt(0))?.requestFocus()
    }

    private fun loadChangelogFromAssets() {
        tvChangelogContent.text = getString(R.string.about_changelog_text)
    }

    private fun setupSaverPagePill(buttonId: Int, timeoutMs: Long) {
        val btn = findViewById<MaterialButton>(buttonId) ?: return
        btn.setOnClickListener {
            prefs.edit().putLong("screensaver_timeout_global_fallback", timeoutMs).apply()
            refreshScreensaverSubpagePills()
            showCenteredPillToast(getString(R.string.menu_screensaver) + " ✓")
        }
    }

    private fun refreshScreensaverSubpagePills() {
        val activeTimeout = prefs.getLong("screensaver_timeout_global_fallback", 120 * 60 * 1000L)
        updateSubpagePillColor(findViewById(R.id.btnPillSaver30), activeTimeout == 30 * 60 * 1000L)
        updateSubpagePillColor(findViewById(R.id.btnPillSaver60), activeTimeout == 60 * 60 * 1000L)
        updateSubpagePillColor(findViewById(R.id.btnPillSaver90), activeTimeout == 90 * 60 * 1000L)
        updateSubpagePillColor(findViewById(R.id.btnPillSaver120), activeTimeout == 120 * 60 * 1000L)
        updateSubpagePillColor(findViewById(R.id.btnPillSaverOff), activeTimeout == 0L)
    }

    private fun hideAllSubPanelsExcept(activePanel: View) {
        val panels = arrayOf(panelTheme, panelLanguage, panelNotifySelect, panelScreensaver, panelAbout)
        panels.forEach { panel ->
            if (panel != activePanel) {
                panel.visibility = View.GONE
                if (isDualScreenMode) {
                    val parentScrollView = panel.parent as? ScrollView
                    parentScrollView?.visibility = View.GONE
                }
            } else {
                if (isDualScreenMode) {
                    val parentScrollView = panel.parent as? ScrollView
                    parentScrollView?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun handleBackNavigation() {
        when (currentMenuLayer) {
            6 -> {
                panelLanguage.visibility = View.GONE
                if (isDualScreenMode) (panelLanguage.parent as? ScrollView)?.visibility = View.GONE
                if (!isDualScreenMode) panelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.settings_title)
                val targetBtn = findViewById<MaterialButton>(R.id.btnChangeLanguage)
                targetBtn?.post { targetBtn.requestFocus() }
            }
            5 -> {
                panelTheme.visibility = View.GONE
                if (isDualScreenMode) (panelTheme.parent as? ScrollView)?.visibility = View.GONE
                if (!isDualScreenMode) panelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.theme_title)
                val targetBtn = findViewById<MaterialButton>(R.id.btnThemeSelect)
                targetBtn?.post { targetBtn.requestFocus() }
            }
            4 -> {
                panelScreensaver.visibility = View.GONE
                if (isDualScreenMode) (panelScreensaver.parent as? ScrollView)?.visibility = View.GONE
                if (!isDualScreenMode) panelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.menu_screensaver)
                val targetBtn = findViewById<MaterialButton>(R.id.btnGlobalScreensaver)
                targetBtn?.post { targetBtn.requestFocus() }
            }
            3 -> {
                panelAbout.visibility = View.GONE
                if (isDualScreenMode) (panelAbout.parent as? ScrollView)?.visibility = View.GONE
                if (!isDualScreenMode) panelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.settings_title)
                val targetBtn = findViewById<MaterialButton>(R.id.btnAboutMenu)
                targetBtn?.post { targetBtn.requestFocus() }
            }
            1 -> {
                panelNotifySelect.visibility = View.GONE
                if (isDualScreenMode) (panelNotifySelect.parent as? ScrollView)?.visibility = View.GONE
                if (!isDualScreenMode) panelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.settings_notify_title)
                val targetBtn = findViewById<MaterialButton>(R.id.btnNotificationsMenu)
                targetBtn?.post { targetBtn.requestFocus() }
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
        val oldContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val dialogScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(210)).apply {
                setMargins(0, toPx(4), 0, toPx(4))
            }
            isVerticalScrollBarEnabled = true
            clipToPadding = false
            clipChildren = false
        }

        val scrollContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipToPadding = false
            clipChildren = false
        }
        dialogScrollView.addView(scrollContainer)

        var firstBtn: MaterialButton? = null
        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        items.forEachIndexed { index, itemText ->
            val customHex = hexColors?.getOrNull(index)

            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, toPx(30), 0, toPx(30))

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, toPx(8), 0, toPx(8))
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 0
                    }
                }
                setOnClickListener { dialog.dismiss(); onItemSelected(index) }
            }
            if (index == 0) firstBtn = btn
            scrollContainer.addView(btn)
        }

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default)
            isAllCaps = false
            textSize = 16f
            isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, toPx(26), 0, toPx(26))
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, toPx(16), 0, toPx(8))
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 0
                }
            }
            setOnClickListener {
                dialog.dismiss()
                if (isDualScreenMode) {
                    handleBackNavigation()
                } else {
                    initPillButtonStates()
                }
            }
        }
        scrollContainer.addView(closeBtn)

        oldContainer?.parent?.let { parent ->
            val group = parent as ViewGroup
            val idx = group.indexOfChild(oldContainer)
            if (idx != -1) {
                group.removeViewAt(idx)
                group.addView(dialogScrollView, idx)
            }
        }

        dialog.setOnCancelListener {
            if (isDualScreenMode) {
                handleBackNavigation()
            } else {
                initPillButtonStates()
            }
        }

        dialog.show()
        firstBtn?.requestFocus()
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pillView = TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(if (isNight) Color.parseColor("#252B2E") else Color.WHITE)
                setStroke(4, if (isNight) Color.WHITE else Color.parseColor("#BDBDBD"))
            }
            setPadding(50, 35, 50, 35)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        val container = FrameLayout(this).apply {
            val params = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, toPx(120))
            }
            layoutParams = params
            addView(pillView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }
        rootLayout.addView(container)
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2200)
    }

    private fun initPillButtonStates() {
        val defaultTimeout = 120 * 60 * 1000L
        if (!prefs.contains("screensaver_timeout_global_fallback")) {
            prefs.edit().putLong("screensaver_timeout_global_fallback", defaultTimeout).apply()
        }

        arrayOf(
            R.id.btnThemeSelect, R.id.btnChangeLanguage, R.id.btnGlobalScreensaver,
            R.id.btnNotificationsMenu, R.id.btnAboutMenu, R.id.btnSubMenuSounds,
            R.id.btnSubMenuPopups
        ).forEach { id -> updatePillVisuals(findViewById<MaterialButton>(id)) }

        updatePillVisuals(findViewById<MaterialButton>(R.id.btnCheckUpdates))

        setupPill(R.id.btnPillSaver30)
        setupPill(R.id.btnPillSaver60)
        setupPill(R.id.btnPillSaver90)
        setupPill(R.id.btnPillSaver120)
        setupPill(R.id.btnPillSaverOff)
    }

    private fun setupPill(buttonId: Int) {
        val btn = findViewById<MaterialButton>(buttonId) ?: return
        btn.shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
    }

    private fun updatePillVisuals(btn: MaterialButton?) {
        if (btn == null) return
        val isAlwaysGreenPill = btn.id == R.id.btnCheckUpdates

        if (isAlwaysGreenPill) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btn.setTextColor(Color.WHITE)
            return
        }

        val normalBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val normalTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)
        btn.backgroundTintList = ColorStateList.valueOf(normalBgColor)
        btn.setTextColor(normalTxtColor)
    }

    private fun checkForUpdatesFromGithub() {
        val apiUrl = "https://api.github.com/repos/Ship-of-Agony/KlippShell4Creality/releases"

        lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.useCaches = false

                connection.setRequestProperty("User-Agent", "KlippShell-App")
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(responseText)

                    if (jsonArray.length() > 0) {
                        val jsonObject = jsonArray.getJSONObject(0)
                        val latestVersionTag = jsonObject.optString("tag_name", "").replace("v", "").trim()

                        val assetsArray = jsonObject.optJSONArray("assets")
                        var downloadUrl = ""
                        if (assetsArray != null && assetsArray.length() > 0) {
                            downloadUrl = assetsArray.optJSONObject(0).optString("browser_download_url", "")
                        }

                        val currentVersionName = try {
                            packageManager.getPackageInfo(packageName, 0).versionName?.replace("v", "")?.trim() ?: "0.8.5"
                        } catch (e: Exception) {
                            "0.8.5"
                        }

                        val latestNumeric = latestVersionTag.replace(".", "").toIntOrNull() ?: 0
                        val currentNumeric = currentVersionName.replace(".", "").toIntOrNull() ?: 0

                        withContext(Dispatchers.Main) {
                            if (latestNumeric > currentNumeric && downloadUrl.isNotEmpty()) {
                                showUpdateAvailableDialog(latestVersionTag, downloadUrl)
                            } else {
                                showCenteredPillToast(getString(R.string.toast_updater_up_to_date))
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showCenteredPillToast(getString(R.string.toast_updater_up_to_date))
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showCenteredPillToast(getString(R.string.toast_updater_error_code, responseCode))
                    }
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "GitHub Update-Check failed", e)
                withContext(Dispatchers.Main) {
                    showCenteredPillToast(getString(R.string.toast_updater_server_error))
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showUpdateAvailableDialog(newVersion: String, downloadUrl: String) {
        val options = arrayOf(getString(R.string.btn_download_now), getString(R.string.btn_later))
        showTvDialog(
            title = getString(R.string.update_available_title, newVersion),
            options
        ) { index ->
            if (index == 0 && downloadUrl.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    showCenteredPillToast(getString(R.string.toast_update_browser_error))
                }
            } else {
                findViewById<MaterialButton>(R.id.btnCheckUpdates).requestFocus()
            }
        }
    }

    private fun showLicensesDialog() {
        val licenseSb = java.lang.StringBuilder()
        try {
            val stream = assets.open("licenses.txt")
            val reader = BufferedReader(InputStreamReader(stream))
            reader.forEachLine { licenseSb.append(it).append("\n") }
        } catch (e: Exception) {
            licenseSb.append("Lizenzen konnten nicht geladen werden.")
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = getString(R.string.license_link_text)
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(230))
            setPadding(12, 12, 12, 12)
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_input_rounded)
        }

        val tvContent = TextView(this).apply {
            text = licenseSb.toString().trim()
            setTextColor(textColor)
            textSize = 14f
        }

        scrollView.addView(tvContent)
        container?.addView(scrollView)

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default)
            isAllCaps = false
            textSize = 16f
            isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, toPx(30), 0, toPx(30))

            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)

            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 0)
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 0
                }
            }
            setOnClickListener {
                dialog.dismiss()
                tvLicensesLink.requestFocus()
            }
        }

        container?.addView(closeBtn)
        dialog.show()
        closeBtn.requestFocus()
    }

    override fun onPause() {
        try {
            val notificationManagerClass = Class.forName("com.shipofagony.klippshell4creality.NotificationManager")
            val dismissMethod = notificationManagerClass.getMethod("dismissActivePopup")
            dismissMethod.invoke(null)
        } catch (e: Exception) {
            Log.e("KlippShell", "NotificationManager dismissal reflection failed", e)
        }
        super.onPause()
    }

    private fun toPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // GEFIXT (Zeile 1173): Der nicht mehr benötigte timingHandler wurde restlos aus onDestroy gelöscht
    override fun onDestroy() {
        super.onDestroy()
    }

    private data class MenuData(val title: String, val iconRes: Int, val action: () -> Unit)
}