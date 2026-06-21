package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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

    private lateinit var scrollPanelSettings: ScrollView
    private lateinit var panelSettings: LinearLayout
    private lateinit var panelTheme: LinearLayout
    private lateinit var panelLanguage: LinearLayout
    private lateinit var panelNotifySelect: LinearLayout
    private lateinit var panelScreensaver: LinearLayout
    private lateinit var panelRole: LinearLayout
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
    private var advancedTabletButton: MaterialButton? = null

    private var btnCompanionToggleProgrammatic: MaterialButton? = null

    private lateinit var btnPillThemeLight: MaterialButton
    private lateinit var btnPillThemeDark: MaterialButton
    private lateinit var btnPillThemeSystem: MaterialButton
    private lateinit var btnPillRoleAuto: MaterialButton
    private lateinit var btnPillRoleMaster: MaterialButton
    private lateinit var btnPillRoleSlave: MaterialButton

    private val targetBorderColor: Int
        get() {
            val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            return if (isNight) Color.parseColor("#4CAF50") else Color.parseColor("#424242")
        }

    private fun toPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "system") ?: "system"
        val config = android.content.res.Configuration(newBase.resources.configuration)

        if (savedLang != "system") {
            val locale = Locale.forLanguageTag(savedLang)
            Locale.setDefault(locale)
            config.setLocale(locale)
        }

        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> {
                config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            AppCompatDelegate.MODE_NIGHT_NO -> {
                config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
        }

        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        val overrideMode = prefs.getInt("layout_mode_override", 0)
        when (overrideMode) {
            1 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        setContentView(R.layout.activity_settings)

        isDualScreenMode = when (overrideMode) {
            1 -> false
            2 -> true
            else -> findViewById<View>(R.id.guidelineCenter) != null
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        scrollPanelSettings = findViewById(R.id.scrollPanelSettings)
        panelSettings = findViewById(R.id.panelSettings)
        panelTheme = findViewById(R.id.panelTheme)
        panelLanguage = findViewById(R.id.panelLanguage)
        panelNotifySelect = findViewById(R.id.panelNotifySelect)
        panelScreensaver = findViewById(R.id.panelScreensaver)
        panelRole = findViewById(R.id.panelRole)
        panelAbout = findViewById(R.id.panelAbout)
        containerLanguageButtons = findViewById(R.id.containerLanguageButtons)

        tvSettingsTitle = findViewById(R.id.tvSettingsTitle)
        tvSettingsTitle.gravity = Gravity.CENTER
        tvSettingsTitle.layoutParams?.width = ViewGroup.LayoutParams.MATCH_PARENT

        tvChangelogContent = findViewById(R.id.tvChangelogContent)
        scrollChangelogBox = findViewById(R.id.scrollChangelogBox)
        tvLicensesLink = findViewById(R.id.tvLicensesLink)

        tvAboutContactLink = findViewById(R.id.tvAboutContactLink)
        tvAboutContactLink.text = getString(R.string.about_contact_text)
        tvAboutContactLink.setTextColor(Color.parseColor("#2196F3"))

        val btnChangeLanguage = findViewById<MaterialButton>(R.id.btnChangeLanguage)
        val btnThemeSelect = findViewById<MaterialButton>(R.id.btnThemeSelect)
        val btnNotificationsMenu = findViewById<MaterialButton>(R.id.btnNotificationsMenu)
        val btnAutoStartToggle = findViewById<MaterialButton>(R.id.btnAutoStartToggle)
        val btnGlobalScreensaver = findViewById<MaterialButton>(R.id.btnGlobalScreensaver)
        val btnRoleSelect = findViewById<MaterialButton>(R.id.btnRoleSelect)
        val btnPipAdbSelect = findViewById<MaterialButton>(R.id.btnPipAdbSelect)
        val btnAboutMenu = findViewById<MaterialButton>(R.id.btnAboutMenu)
        val btnResetApp = findViewById<MaterialButton>(R.id.btnResetApp)
        val btnSettingsBack = findViewById<MaterialButton>(R.id.btnSettingsBack)

        btnPillThemeLight = findViewById(R.id.btnPillThemeLight)
        btnPillThemeDark = findViewById(R.id.btnPillThemeDark)
        btnPillThemeSystem = findViewById(R.id.btnPillThemeSystem)

        btnPillRoleAuto = findViewById(R.id.btnPillRoleAuto)
        btnPillRoleMaster = findViewById(R.id.btnPillRoleMaster)
        btnPillRoleSlave = findViewById(R.id.btnPillRoleSlave)

        val btnSubMenuSounds = findViewById<MaterialButton>(R.id.btnSubMenuSounds)
        val btnSubMenuPopups = findViewById<MaterialButton>(R.id.btnSubMenuPopups)

        val btnCheckUpdates = findViewById<MaterialButton>(R.id.btnCheckUpdates)
        val btnSettingsFaq = findViewById<MaterialButton>(R.id.btnSettingsFaq)
        val ivAboutStudioLogo = findViewById<ImageView>(R.id.ivAboutStudioLogo)

        val pVertPill = if (isDualScreenMode) toPx(30) else toPx(14)

        val toggleBtn = MaterialButton(this).apply {
            isAllCaps = false
            textSize = 16f
            isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, pVertPill, 0, pVertPill)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, toPx(8), 0, toPx(24))
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    if (hasFocus) {
                        v.strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else {
                        v.strokeWidth = 0
                    }
                }
            }
            setOnClickListener {
                val currentRole = prefs.getString("app_device_role", "auto") ?: "auto"
                if (currentRole == "disabled") {
                    prefs.edit().putString("app_device_role", "auto").apply()
                    showCenteredPillToast(getString(R.string.companion_toggle_on) + " ✓")
                } else {
                    prefs.edit().putString("app_device_role", "disabled").apply()
                    showCenteredPillToast(getString(R.string.companion_toggle_off) + " ✓")
                }
                refreshCompanionToggleAndPills(this, btnPillRoleAuto, btnPillRoleMaster, btnPillRoleSlave)
            }
        }
        btnCompanionToggleProgrammatic = toggleBtn
        panelRole.addView(toggleBtn, 0)

        btnChangeLanguage.requestFocus()

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            findViewById<TextView>(R.id.tvAppVersion)?.text = "Version $versionName"
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tvAppVersion)?.text = "Version 0.8.8.110626-rc"
        }

        btnThemeSelect.setOnClickListener {
            showSubPanel(panelTheme, 5, getString(R.string.theme_title))
            refreshThemeSubpagePills()

            val currentMode = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            when (currentMode) {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> btnPillThemeSystem.requestFocus()
                AppCompatDelegate.MODE_NIGHT_NO -> btnPillThemeLight.requestFocus()
                else -> btnPillThemeDark.requestFocus()
            }
        }

        val intentThemeClick = View.OnClickListener { view ->
            val targetMode = when (view.id) {
                R.id.btnPillThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.btnPillThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("app_theme", targetMode).apply()
            AppCompatDelegate.setDefaultNightMode(targetMode)
            recreate()
        }
        btnPillThemeLight.setOnClickListener(intentThemeClick)
        btnPillThemeDark.setOnClickListener(intentThemeClick)
        btnPillThemeSystem.setOnClickListener(intentThemeClick)

        btnChangeLanguage.setOnClickListener {
            showSubPanel(panelLanguage, 6, getString(R.string.change_language))
            buildDynamicLanguageMenu()
        }

        btnGlobalScreensaver.setOnClickListener {
            showSubPanel(panelScreensaver, 4, getString(R.string.menu_screensaver))
            refreshScreensaverSubpagePills()
            findViewById<MaterialButton>(R.id.btnPillSaver30)?.requestFocus()
        }

        btnAutoStartToggle.setOnClickListener {
            val current = prefs.getBoolean("auto_start_printer", false)
            prefs.edit().putBoolean("auto_start_printer", !current).apply()
            updateAutoStartButtonVisuals(btnAutoStartToggle)
            showCenteredPillToast(if (!current) getString(R.string.toast_autostart_on) else getString(R.string.toast_autostart_off))
        }

        btnRoleSelect.setOnClickListener {
            showSubPanel(panelRole, 7, getString(R.string.settings_role_title))
            btnCompanionToggleProgrammatic?.let { toggleBtn ->
                refreshCompanionToggleAndPills(toggleBtn, btnPillRoleAuto, btnPillRoleMaster, btnPillRoleSlave)
                toggleBtn.requestFocus()
            }
        }

        val roleClick = View.OnClickListener { view ->
            val targetRole = when (view.id) {
                R.id.btnPillRoleMaster -> "master"
                R.id.btnPillRoleSlave -> "slave"
                else -> "auto"
            }
            prefs.edit().putString("app_device_role", targetRole).apply()
            btnCompanionToggleProgrammatic?.let { refreshCompanionToggleAndPills(it, btnPillRoleAuto, btnPillRoleMaster, btnPillRoleSlave) }
            showCenteredPillToast(getString(R.string.settings_role_title) + " ✓")
        }
        btnPillRoleAuto.setOnClickListener(roleClick)
        btnPillRoleMaster.setOnClickListener(roleClick)
        btnPillRoleSlave.setOnClickListener(roleClick)

        btnPipAdbSelect.setOnClickListener { showPipAdbGuideDialog() }
        btnNotificationsMenu.setOnClickListener {
            showSubPanel(panelNotifySelect, 1, getString(R.string.settings_notify_title))
            btnSubMenuSounds.requestFocus()
        }

        btnSubMenuSounds.setOnClickListener { showSoundsConfigurationDialog() }
        btnSubMenuPopups.setOnClickListener { showPopupsConfigurationDialog() }

        btnAboutMenu.setOnClickListener {
            showSubPanel(panelAbout, 3, getString(R.string.btn_about_menu))
            loadChangelogFromAssets()
            btnSettingsFaq?.requestFocus() ?: btnCheckUpdates.requestFocus()
        }

        btnResetApp.setOnClickListener {
            val options = arrayOf(getString(R.string.reset_app_yes), getString(R.string.reset_app_cancel))
            showTvDialog(getString(R.string.reset_app_title), options, arrayOf("#E53935", null)) { index ->
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

        btnCheckUpdates.setOnClickListener { showUpdateSubMenuDialog() }
        btnSettingsFaq?.setOnClickListener { showFaqDialog() }
        tvLicensesLink.setOnClickListener { showLicensesDialog() }

        tvLicensesLink.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(150).start()
            (v as TextView).setTextColor(if (hasFocus) targetBorderColor else Color.parseColor("#2196F3"))
        }

        tvAboutContactLink.setOnClickListener {
            val contactOptions = arrayOf("GitHub Repository", getString(R.string.about_contact_option))
            showTvDialog(getString(R.string.studio_name), contactOptions, arrayOf("#4CAF50", "#2196F3")) { choice ->
                if (choice == 0) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ship-of-Agony/KlippShell4Creality")))
                } else {
                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL, arrayOf("klippshell@gmail.com"))
                        putExtra(Intent.EXTRA_SUBJECT, "KlippShell Support Request")
                    }
                    try { startActivity(Intent.createChooser(emailIntent, "Send Mail...")) } catch (e: Exception) { showCenteredPillToast(getString(R.string.toast_email_error)) }
                }
            }
        }
        tvAboutContactLink.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.08f else 1.0f).scaleY(if (hasFocus) 1.08f else 1.0f).setDuration(150).start()
            (v as TextView).setTextColor(if (hasFocus) targetBorderColor else Color.parseColor("#2196F3"))
        }

        setupSaverPagePill(R.id.btnPillSaver30, 30 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaver60, 60 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaver90, 90 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaver120, 120 * 60 * 1000L)
        setupSaverPagePill(R.id.btnPillSaverOff, 0L)

        btnSettingsBack.setOnClickListener { handleBackNavigation() }

        scrollChangelogBox.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            v.animate().scaleX(if (hasFocus) 1.02f else 1.0f).scaleY(if (hasFocus) 1.02f else 1.0f).setDuration(150).start()
            val bgDrawable = v.background as? GradientDrawable
            if (bgDrawable != null) {
                val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (hasFocus) {
                    bgDrawable.setStroke(8, targetBorderColor)
                } else {
                    bgDrawable.setStroke(3, Color.parseColor(if (isNight) "#40FFFFFF" else "#33000000"))
                }
            }
        }

        arrayOf(
            btnChangeLanguage, btnThemeSelect, btnNotificationsMenu, btnAutoStartToggle, btnGlobalScreensaver, btnRoleSelect, btnPipAdbSelect,
            btnAboutMenu, btnResetApp, btnSubMenuSounds, btnSubMenuPopups,
            btnPillThemeLight, btnPillThemeDark, btnPillThemeSystem, btnSettingsBack,
            btnPillRoleAuto, btnPillRoleMaster, btnPillRoleSlave, btnSettingsFaq
        ).forEach { btn ->
            btn?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    if (hasFocus) {
                        v.strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else {
                        v.strokeWidth = 0
                    }
                }
                if (v == btnAutoStartToggle) updateAutoStartButtonVisuals(btnAutoStartToggle)
            }
        }

        initPillButtonStates()

        // =========================================================================
        // FIX: PARSE EINGEHENDEN INTENT DIREKT IN ONCREATE FÜR DIREKT-WEITERLEITUNG
        // =========================================================================
        val layerFromIntent = intent?.getIntExtra("saved_menu_layer", 0) ?: 0

        if (savedInstanceState != null) {
            currentMenuLayer = savedInstanceState.getInt("saved_menu_layer", 0)
            restoreMenuState()
        } else if (layerFromIntent != 0) {
            currentMenuLayer = layerFromIntent
            restoreMenuState()
        } else {
            if (isDualScreenMode) {
                showSubPanel(panelAbout, 3, getString(R.string.btn_about_menu))
                loadChangelogFromAssets()
            } else {
                val subPanels = arrayOf(panelTheme, panelLanguage, panelNotifySelect, panelScreensaver, panelRole, panelAbout)
                subPanels.forEach { panel ->
                    panel.visibility = View.GONE
                    (panel.parent as? ScrollView)?.visibility = View.GONE
                }
                scrollPanelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.settings_title)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        recreate()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("saved_menu_layer", currentMenuLayer)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentMenuLayer = savedInstanceState.getInt("saved_menu_layer", 0)
        restoreMenuState()
    }

    private fun restoreMenuState() {
        if (currentMenuLayer == 0) {
            if (!isDualScreenMode) {
                scrollPanelSettings.visibility = View.VISIBLE
                tvSettingsTitle.text = getString(R.string.settings_title)
                arrayOf(panelTheme, panelLanguage, panelNotifySelect, panelScreensaver, panelRole, panelAbout).forEach { panel ->
                    panel.visibility = View.GONE
                    (panel.parent as? ScrollView)?.visibility = View.GONE
                }
            } else {
                currentMenuLayer = 3
                showSubPanel(panelAbout, 3, getString(R.string.btn_about_menu))
                loadChangelogFromAssets()
            }
        } else {
            val panelMap = mapOf(
                5 to Pair(panelTheme, R.string.theme_title),
                6 to Pair(panelLanguage, R.string.change_language),
                1 to Pair(panelNotifySelect, R.string.settings_notify_title),
                4 to Pair(panelScreensaver, R.string.menu_screensaver),
                7 to Pair(panelRole, R.string.settings_role_title),
                3 to Pair(panelAbout, R.string.btn_about_menu)
            )

            val active = panelMap[currentMenuLayer]
            if (active != null) {
                showSubPanel(active.first, currentMenuLayer, getString(active.second))

                when (currentMenuLayer) {
                    6 -> buildDynamicLanguageMenu()
                    5 -> refreshThemeSubpagePills()
                    4 -> refreshScreensaverSubpagePills()
                    7 -> btnCompanionToggleProgrammatic?.let { refreshCompanionToggleAndPills(it, btnPillRoleAuto, btnPillRoleMaster, btnPillRoleSlave) }
                    3 -> loadChangelogFromAssets()
                }
            } else {
                currentMenuLayer = 0
                restoreMenuState()
            }
        }
    }

    private fun refreshThemeSubpagePills() {
        val currentMode = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        updateSubpagePillColor(btnPillThemeLight, currentMode == AppCompatDelegate.MODE_NIGHT_NO)
        updateSubpagePillColor(btnPillThemeDark, currentMode == AppCompatDelegate.MODE_NIGHT_YES)
        updateSubpagePillColor(btnPillThemeSystem, currentMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun refreshCompanionToggleAndPills(toggleBtn: MaterialButton, autoBtn: MaterialButton, masterBtn: MaterialButton, slaveBtn: MaterialButton) {
        val currentRole = prefs.getString("app_device_role", "auto") ?: "auto"
        val isEnabled = currentRole != "disabled"

        if (isEnabled) {
            toggleBtn.text = getString(R.string.companion_toggle_on)
            toggleBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            toggleBtn.setTextColor(Color.WHITE)

            autoBtn.isEnabled = true
            masterBtn.isEnabled = true
            slaveBtn.isEnabled = true

            updateSubpagePillColor(autoBtn, currentRole == "auto")
            updateSubpagePillColor(masterBtn, currentRole == "master")
            updateSubpagePillColor(slaveBtn, currentRole == "slave")
        } else {
            toggleBtn.text = getString(R.string.companion_toggle_off)
            toggleBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            toggleBtn.setTextColor(Color.WHITE)

            autoBtn.isEnabled = false
            masterBtn.isEnabled = false
            slaveBtn.isEnabled = false

            updateSubpagePillColor(autoBtn, false)
            updateSubpagePillColor(masterBtn, false)
            updateSubpagePillColor(slaveBtn, false)
        }
    }

    private fun showPipAdbGuideDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = getString(R.string.dialog_pip_adb_guide_title)
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(250))
            setPadding(12, 12, 12, 12)
            background = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.bg_input_rounded)
        }

        val tvContent = TextView(this).apply {
            text = Html.fromHtml(getString(R.string.dialog_pip_adb_guide_text), Html.FROM_HTML_MODE_LEGACY)
            setTextColor(textColor)
            textSize = 14f
            setLineSpacing(0f, 1.2f)
        }

        scrollView.addView(tvContent)
        container?.addView(scrollView)

        val pVert = if (isDualScreenMode) toPx(30) else toPx(14)

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default)
            isAllCaps = false
            textSize = 16f
            isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, pVert, 0, pVert)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 16, 0, 0)
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 0
                }
            }
            setOnClickListener {
                dialog.dismiss()
                findViewById<MaterialButton>(R.id.btnPipAdbSelect)?.requestFocus()
            }
        }

        container?.addView(closeBtn)
        dialog.show()
        closeBtn.requestFocus()
    }

    private fun showSubPanel(activePanel: View, layer: Int, title: String) {
        currentMenuLayer = layer
        tvSettingsTitle.text = title

        if (!isDualScreenMode) {
            scrollPanelSettings.visibility = View.GONE
        }

        val panels = arrayOf(panelTheme, panelLanguage, panelNotifySelect, panelScreensaver, panelRole, panelAbout)
        panels.forEach { panel ->
            val parentScrollView = panel.parent as? ScrollView
            if (panel == activePanel) {
                panel.visibility = View.VISIBLE
                parentScrollView?.visibility = View.VISIBLE
            } else {
                panel.visibility = View.GONE
                parentScrollView?.visibility = View.GONE
            }
        }

        if (isDualScreenMode) {
            updateMenuButtonSelection(layer)
        }
    }

    private fun updateSubpagePillColor(btn: MaterialButton?, isSelected: Boolean) {
        if (btn == null) return

        if (isSelected) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btn.setTextColor(Color.WHITE)
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.pill_normal_inactive))
            btn.setTextColor(ContextCompat.getColor(this, R.color.pill_normal_inactive_text))
        }

        if (btn.isFocused) {
            btn.strokeWidth = 8
            btn.strokeColor = ColorStateList.valueOf(targetBorderColor)
        } else {
            btn.strokeWidth = 0
            btn.strokeColor = null
        }
    }

    private fun updateAutoStartButtonVisuals(btn: MaterialButton) {
        val isEnabled = prefs.getBoolean("auto_start_printer", false)
        btn.text = if (isEnabled) getString(R.string.autostart_enabled) else getString(R.string.autostart_disabled)

        if (isEnabled) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btn.setTextColor(Color.WHITE)
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.pill_normal_inactive))
            btn.setTextColor(ContextCompat.getColor(this, R.color.pill_normal_inactive_text))
        }

        if (btn.isFocused) {
            btn.strokeWidth = 8
            btn.strokeColor = ColorStateList.valueOf(targetBorderColor)
        } else {
            btn.strokeWidth = 0
            btn.strokeColor = null
        }
    }

    private fun updateTabletButtonVisuals(btn: MaterialButton, overrideMode: Int) {
        btn.text = when (overrideMode) {
            1 -> getString(R.string.layout_mode_phone)
            2 -> getString(R.string.layout_mode_tablet)
            else -> getString(R.string.layout_mode_auto)
        }

        when (overrideMode) {
            1 -> {
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
                btn.setTextColor(Color.WHITE)
            }
            2 -> {
                btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                btn.setTextColor(Color.WHITE)
            }
            else -> {
                btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.pill_normal_inactive))
                btn.setTextColor(ContextCompat.getColor(this, R.color.pill_normal_inactive_text))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun checkAndRenderAdvancedMenu() {
        val isAdvancedActive = prefs.getBoolean("is_advanced_mode", false)
        if (!isAdvancedActive) return

        if (advancedHeaderView != null || advancedTvButton != null || advancedTabletButton != null) return

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
            isAllCaps = false; textSize = 16f; isFocusable = true
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
                    if (hasFocus) {
                        v.strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else {
                        v.strokeWidth = 0
                    }
                }
            }
            setOnClickListener {
                try {
                    val workClass = Class.forName("com.shipofagony.klippshell4creality.KlipperTvWorker") as Class<out androidx.work.ListenableWorker>
                    WorkManager.getInstance(applicationContext).enqueue(OneTimeWorkRequest.Builder(workClass).build())
                    showCenteredPillToast(getString(R.string.btn_advanced_trigger_tv) + " ✓")
                } catch (e: Exception) { Log.e("KlippShell", "Advanced TV-Worker Trigger failed", e) }
            }
        }

        advancedTabletButton = MaterialButton(this).apply {
            isAllCaps = false; textSize = 16f; isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, toPx(14), 0, toPx(14))

            val overrideMode = prefs.getInt("layout_mode_override", 0)
            val finalOverride = overrideMode
            updateTabletButtonVisuals(this, finalOverride)

            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, toPx(8), 0, toPx(8))
            }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    if (hasFocus) {
                        v.strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else {
                        v.strokeWidth = 0
                    }
                }
            }
            setOnClickListener {
                val currentMode = prefs.getInt("layout_mode_override", 0)
                val nextMode = (currentMode + 1) % 3
                prefs.edit().putInt("layout_mode_override", nextMode).apply()
                updateTabletButtonVisuals(this, nextMode)

                val toastText = when (nextMode) {
                    1 -> getString(R.string.layout_mode_phone)
                    2 -> getString(R.string.layout_mode_tablet)
                    else -> getString(R.string.layout_mode_auto)
                }
                showCenteredPillToast("$toastText ✓")
                recreate()
            }
        }

        mainContainer.addView(advancedHeaderView, indexReset + 1)
        mainContainer.addView(advancedTvButton, indexReset + 2)
        mainContainer.addView(advancedTabletButton, indexReset + 3)
    }

    private fun removeAdvancedMenuViews() {
        val mainContainer = findViewById<LinearLayout>(R.id.panelSettings) ?: return
        advancedHeaderView?.let { mainContainer.removeView(it); advancedHeaderView = null }
        advancedTvButton?.let { mainContainer.removeView(it); advancedTvButton = null }
        advancedTabletButton?.let { mainContainer.removeView(it); advancedTabletButton = null }
    }

    private fun updateMenuButtonSelection(activeLayer: Int) {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val activeBg = ColorStateList.valueOf(if (isNight) Color.parseColor("#44FFFFFF") else Color.parseColor("#1A888888"))
        val activeTxt = if (isNight) Color.WHITE else Color.BLACK

        val normalBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val normalTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val menuButtons = arrayOf(
            R.id.btnChangeLanguage to 6,
            R.id.btnThemeSelect to 5,
            R.id.btnNotificationsMenu to 1,
            R.id.btnGlobalScreensaver to 4,
            R.id.btnRoleSelect to 7,
            R.id.btnAboutMenu to 3
        )

        menuButtons.forEach { (btnId, layerId) ->
            findViewById<MaterialButton>(btnId)?.let { btn ->
                if (activeLayer == layerId) {
                    btn.backgroundTintList = activeBg
                    btn.setTextColor(activeTxt)
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(normalBgColor)
                    btn.setTextColor(normalTxtColor)
                }
                if (btn.isFocused) {
                    btn.strokeWidth = 8
                    btn.strokeColor = ColorStateList.valueOf(targetBorderColor)
                } else {
                    btn.strokeWidth = 0
                    btn.strokeColor = null
                }
            }
        }
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
        val soundKeys = arrayOf("sound_offline", "sound_first_layer", "sound_50", "sound_75", "sound_90", "sound_100")
        val soundTitles = arrayOf(
            getString(R.string.notify_title_offline), getString(R.string.notify_title_first_layer),
            getString(R.string.notify_title_50), getString(R.string.notify_title_75),
            getString(R.string.notify_title_90), getString(R.string.notify_title_100)
        )

        val options = Array(soundKeys.size) { i ->
            val isEnabled = prefs.getBoolean(soundKeys[i], true)
            soundTitles[i] + if (isEnabled) " ✓" else ""
        }
        val hexColors = Array<String?>(soundKeys.size) { i -> if (prefs.getBoolean(soundKeys[i], true)) "#4CAF50" else null }

        showTvDialog(getString(R.string.submenu_sounds_title), options, hexColors) { index ->
            val targetKey = soundKeys[index]
            val newVal = !prefs.getBoolean(targetKey, true)
            prefs.edit().putBoolean(targetKey, newVal).apply()

            if (newVal) {
                this@SettingsActivity.showCenteredPillToast("🔊 Ton aktiviert: " + soundTitles[index] + " ✓")
                try { SoundManager.playLiveNotification(targetKey) } catch (e: Exception) { Log.e("KlippShell", "SoundManager Preview trigger failed", e) }
            }
            showSoundsConfigurationDialog()
        }
    }

    private fun showPopupsConfigurationDialog() {
        val popupKeys = arrayOf("popup_offline", "popup_first_layer", "popup_50", "popup_75", "popup_90", "popup_100")
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
        val hexColors = Array<String?>(popupKeys.size) { i -> if (prefs.getBoolean(popupKeys[i], true)) "#4CAF50" else null }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = getString(R.string.submenu_popups_title)
        val oldContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val dialogScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(210)).apply { setMargins(0, toPx(4), 0, toPx(4)) }
            isVerticalScrollBarEnabled = true
        }

        val scrollContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dialogScrollView.addView(scrollContainer)

        var firstBtn: MaterialButton? = null
        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val pVert = if (isDualScreenMode) toPx(30) else toPx(14)
        val pVertClose = if (isDualScreenMode) toPx(26) else toPx(14)

        options.forEachIndexed { index, itemText ->
            val customHex = hexColors.getOrNull(index)
            val btn = MaterialButton(this).apply {
                text = itemText; isAllCaps = false; textSize = 16f; isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, pVert, 0, pVert)

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(8), 0, toPx(8)) }
                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
                }

                setOnClickListener {
                    val targetKey = popupKeys[index]
                    val newVal = !prefs.getBoolean(targetKey, true)
                    prefs.edit().putBoolean(targetKey, newVal).apply()
                    dialog.dismiss()

                    if (newVal) {
                        NotificationManager.showLivePopup(this@SettingsActivity, targetKey, popupTitleIds[index], popupMessages[index]) {
                            showPopupsConfigurationDialog()
                        }
                    } else { showPopupsConfigurationDialog() }
                }
            }
            if (index == 0) firstBtn = btn
            scrollContainer.addView(btn)
        }

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default)
            isAllCaps = false; textSize = 16f; isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, pVertClose, 0, pVertClose)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(16), 0, toPx(8)) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
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
            if (idx != -1) { group.removeViewAt(idx); group.addView(dialogScrollView, idx) }
        }
        dialog.setOnCancelListener { if (isDualScreenMode) handleBackNavigation() else initPillButtonStates() }
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

        val pVert = if (isDualScreenMode) toPx(30) else toPx(14)

        names.forEachIndexed { index, langName ->
            val isSelected = codes[index] == activeCode
            val btn = MaterialButton(this).apply {
                text = langName; isAllCaps = false; textSize = 16f; isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, pVert, 0, pVert)

                if (isSelected) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(8), 0, toPx(8)) }
                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
                }

                setOnClickListener {
                    val selectedCode = codes[index]
                    prefs.edit().putString("app_lang", selectedCode).apply()

                    val locale = if (selectedCode == "system") Locale.getDefault() else Locale.forLanguageTag(selectedCode)
                    Locale.setDefault(locale)

                    val config = Configuration(resources.configuration)
                    config.setLocale(locale)
                    createConfigurationContext(config)

                    val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(intent)
                    finish()
                }
            }
            if (isSelected) preFocused = btn
            containerLanguageButtons.addView(btn)
        }
        (preFocused ?: containerLanguageButtons.getChildAt(0) as? MaterialButton)?.requestFocus()
    }

    private fun loadChangelogFromAssets() {
        val changelogSb = java.lang.StringBuilder()
        try {
            val stream = assets.open("changelog.txt")
            val reader = BufferedReader(InputStreamReader(stream))
            reader.forEachLine { changelogSb.append(it).append("\n") }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Lesen der changelog.txt", e)
            changelogSb.append("Changelog konnte nicht geladen werden.")
        }
        tvChangelogContent.text = changelogSb.toString().trim()
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

    private fun handleBackNavigation() {
        if (isDualScreenMode) {
            finish()
        } else {
            if (scrollPanelSettings.visibility == View.VISIBLE) {
                finish()
            } else {
                val oldLayer = currentMenuLayer
                val panels = arrayOf(panelTheme, panelLanguage, panelNotifySelect, panelScreensaver, panelRole, panelAbout)
                panels.forEach { panel ->
                    panel.visibility = View.GONE
                    (panel.parent as? ScrollView)?.visibility = View.GONE
                }
                scrollPanelSettings.visibility = View.VISIBLE
                currentMenuLayer = 0
                tvSettingsTitle.text = getString(R.string.settings_title)

                initPillButtonStates()

                val targetBtnId = when (oldLayer) {
                    6 -> R.id.btnChangeLanguage
                    5 -> R.id.btnThemeSelect
                    1 -> R.id.btnNotificationsMenu
                    4 -> R.id.btnGlobalScreensaver
                    7 -> R.id.btnRoleSelect
                    3 -> R.id.btnAboutMenu
                    else -> R.id.btnChangeLanguage
                }
                findViewById<View>(targetBtnId)?.requestFocus()
            }
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
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(210)).apply { setMargins(0, toPx(4), 0, toPx(4)) }
            isVerticalScrollBarEnabled = true
            clipToPadding = false; clipChildren = false
        }

        val scrollContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipToPadding = false; clipChildren = false
        }
        dialogScrollView.addView(scrollContainer)

        var firstBtn: MaterialButton? = null
        val defaultBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val defaultTxtColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val pVert = if (isDualScreenMode) toPx(30) else toPx(14)
        val pVertClose = if (isDualScreenMode) toPx(26) else toPx(14)

        items.forEachIndexed { index, itemText ->
            val customHex = hexColors?.getOrNull(index)
            val btn = MaterialButton(this).apply {
                text = itemText; isAllCaps = false; textSize = 16f; isFocusable = true
                shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                setPadding(0, pVert, 0, pVert)

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(defaultBgColor)
                    setTextColor(defaultTxtColor)
                }

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(8), 0, toPx(8)) }
                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                    } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
                }
                setOnClickListener { dialog.dismiss(); onItemSelected(index) }
            }
            if (index == 0) firstBtn = btn
            scrollContainer.addView(btn)
        }

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default); isAllCaps = false; textSize = 16f; isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, pVertClose, 0, pVertClose)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(16), 0, toPx(8)) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
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
            if (idx != -1) { group.removeViewAt(idx); group.addView(dialogScrollView, idx) }
        }
        dialog.setOnCancelListener { if (isDualScreenMode) handleBackNavigation() else initPillButtonStates() }
        dialog.show()
        firstBtn?.requestFocus()
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pillView = TextView(this).apply {
            text = message; textSize = 16f; gravity = Gravity.CENTER; setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 100f
                setColor(if (isNight) Color.parseColor("#252B2E") else Color.WHITE)
                setStroke(4, if (isNight) Color.WHITE else Color.parseColor("#BDBDBD"))
            }
            setPadding(50, 35, 50, 35)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, toPx(120))
            }
            addView(pillView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }
        rootLayout.addView(container)
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2200)
    }

    private fun showFaqDialog() {
        val faqSb = java.lang.StringBuilder()
        try {
            val stream = assets.open("faq.txt")
            val reader = BufferedReader(InputStreamReader(stream))
            reader.forEachLine { faqSb.append(it).append("\n") }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Lesen der faq.txt", e)
            faqSb.append("FAQ konnte nicht geladen werden.")
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "FAQ"
        val oldContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val dialogScrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(210)).apply { setMargins(0, toPx(4), 0, toPx(4)) }
            isVerticalScrollBarEnabled = true
            clipToPadding = false; clipChildren = false
        }

        val scrollContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            clipToPadding = false; clipChildren = false
        }
        dialogScrollView.addView(scrollContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK

        val tvContent = TextView(this).apply {
            text = faqSb.toString().trim()
            setTextColor(textColor)
            textSize = 14f
        }
        scrollContainer.addView(tvContent)

        val pVertClose = if (isDualScreenMode) toPx(26) else toPx(14)

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default)
            isAllCaps = false; textSize = 16f; isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, pVertClose, 0, pVertClose)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(16), 0, toPx(8)) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8
                    v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
            }
            setOnClickListener {
                dialog.dismiss()
                findViewById<MaterialButton>(R.id.btnSettingsFaq)?.requestFocus()
            }
        }
        scrollContainer.addView(closeBtn)

        oldContainer?.parent?.let { parent ->
            val group = parent as ViewGroup
            val idx = group.indexOfChild(oldContainer)
            if (idx != -1) { group.removeViewAt(idx); group.addView(dialogScrollView, idx) }
        }
        dialog.setOnCancelListener { if (isDualScreenMode) handleBackNavigation() else initPillButtonStates() }
        dialog.show()
        closeBtn.requestFocus()
    }

    private fun initPillButtonStates() {
        val defaultTimeout = 120 * 60 * 1000L
        if (!prefs.contains("screensaver_timeout_global_fallback")) {
            prefs.edit().putLong("screensaver_timeout_global_fallback", defaultTimeout).apply()
        }

        arrayOf(
            R.id.btnChangeLanguage, R.id.btnThemeSelect, R.id.btnNotificationsMenu, R.id.btnAutoStartToggle,
            R.id.btnGlobalScreensaver, R.id.btnRoleSelect, R.id.btnPipAdbSelect,
            R.id.btnAboutMenu, R.id.btnSubMenuSounds, R.id.btnSubMenuPopups, R.id.btnResetApp
        ).forEach { id -> updatePillVisuals(findViewById(id)) }

        updatePillVisuals(findViewById(R.id.btnCheckUpdates))
        updatePillVisuals(findViewById(R.id.btnSettingsFaq))
        updateAutoStartButtonVisuals(findViewById(R.id.btnAutoStartToggle))

        refreshThemeSubpagePills()

        btnCompanionToggleProgrammatic?.let { toggleBtn ->
            refreshCompanionToggleAndPills(
                toggleBtn,
                btnPillRoleAuto,
                btnPillRoleMaster,
                btnPillRoleSlave
            )
        }

        setupPill(R.id.btnPillSaver30)
        setupPill(R.id.btnPillSaver60)
        setupPill(R.id.btnPillSaver90)
        setupPill(R.id.btnPillSaver120)
        setupPill(R.id.btnPillSaverOff)

        setupPill(R.id.btnPillRoleAuto)
        setupPill(R.id.btnPillRoleMaster)
        setupPill(R.id.btnPillRoleSlave)

        checkAndRenderAdvancedMenu()
    }

    private fun setupPill(buttonId: Int) {
        val btn = findViewById<MaterialButton>(buttonId) ?: return
        btn.shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
    }

    private fun updatePillVisuals(btn: MaterialButton?) {
        if (btn == null) return
        if (btn.id == R.id.btnCheckUpdates) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btn.setTextColor(Color.WHITE)
            return
        }

        if (btn.id == R.id.btnSettingsFaq) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            btn.setTextColor(Color.WHITE)
            return
        }

        if (btn.id == R.id.btnResetApp) {
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
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

                        val currentVersionName = try { packageManager.getPackageInfo(packageName, 0).versionName?.replace("v", "")?.trim() ?: "0.8.6" } catch (e: Exception) { "0.8.6" }
                        val cleanCurrent = currentVersionName.takeWhile { it.isDigit() || it == '.' }.trim('.')
                        val cleanLatest = latestVersionTag.takeWhile { it.isDigit() || it == '.' }.trim('.')

                        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
                        val latestParts = cleanLatest.split(".").map { it.toIntOrNull() ?: 0 }

                        var isNewer = false
                        val maxLength = maxOf(currentParts.size, latestParts.size)
                        for (i in 0 until maxLength) {
                            val currentPart = currentParts.getOrElse(i) { 0 }
                            val latestPart = latestParts.getOrElse(i) { 0 }
                            if (latestPart > currentPart) { isNewer = true; break }
                            if (latestPart < currentPart) { isNewer = false; break }
                        }

                        withContext(Dispatchers.Main) {
                            if (isNewer && downloadUrl.isNotEmpty()) { showUpdateAvailableDialog(latestVersionTag, downloadUrl) }
                            else { showCenteredPillToast(getString(R.string.toast_updater_up_to_date)) }
                        }
                    } else { withContext(Dispatchers.Main) { showCenteredPillToast(getString(R.string.toast_updater_up_to_date)) } }
                } else { withContext(Dispatchers.Main) { showCenteredPillToast(getString(R.string.toast_updater_error_code, responseCode)) } }
            } catch (e: Exception) {
                Log.e("KlippShell", "GitHub Update-Check failed", e)
                withContext(Dispatchers.Main) { showCenteredPillToast(getString(R.string.toast_updater_server_error)) }
            } finally { connection?.disconnect() }
        }
    }

    private fun showUpdateAvailableDialog(newVersion: String, downloadUrl: String) {
        val options = arrayOf(getString(R.string.btn_download_now), getString(R.string.btn_later))
        showTvDialog(getString(R.string.update_available_title, newVersion), options) { index ->
            if (index == 0 && downloadUrl.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(intent)
                } catch (e: Exception) { showCenteredPillToast(getString(R.string.toast_update_browser_error)) }
            } else { findViewById<MaterialButton>(R.id.btnCheckUpdates).requestFocus() }
        }
    }

    private fun showLicensesDialog() {
        val licenseSb = java.lang.StringBuilder()
        try {
            val stream = assets.open("licenses.txt")
            val reader = BufferedReader(InputStreamReader(stream))
            reader.forEachLine { licenseSb.append(it).append("\n") }
        } catch (e: Exception) { licenseSb.append("Lizenzen konnten nicht geladen werden.") }

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

        val tvContent = TextView(this).apply { text = licenseSb.toString().trim(); setTextColor(textColor); textSize = 14f }
        scrollView.addView(tvContent); container?.addView(scrollView)

        val pVertClose = if (isDualScreenMode) toPx(30) else toPx(14)

        val closeBtn = MaterialButton(this).apply {
            text = getString(R.string.notify_btn_default); isAllCaps = false; textSize = 16f; isFocusable = true
            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
            setPadding(0, pVertClose, 0, pVertClose); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(100).start()
                    (v as MaterialButton).strokeWidth = 8; v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                } else { v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
            }
            setOnClickListener { dialog.dismiss(); tvLicensesLink.requestFocus() }
        }
        container?.addView(closeBtn); dialog.show(); closeBtn.requestFocus()
    }

    private fun applyUnifiedButtonShapesAndFocus() {
        arrayOf(
            findViewById<MaterialButton>(R.id.btnChangeLanguage), findViewById<MaterialButton>(R.id.btnThemeSelect),
            findViewById<MaterialButton>(R.id.btnNotificationsMenu), findViewById<MaterialButton>(R.id.btnAutoStartToggle),
            findViewById<MaterialButton>(R.id.btnGlobalScreensaver), findViewById<MaterialButton>(R.id.btnRoleSelect),
            findViewById<MaterialButton>(R.id.btnPipAdbSelect), findViewById<MaterialButton>(R.id.btnAboutMenu),
            findViewById<MaterialButton>(R.id.btnResetApp), findViewById<MaterialButton>(R.id.btnSettingsBack)
        ).forEach { btn ->
            btn?.let {
                it.shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCorners(CornerFamily.ROUNDED, 100f).build()
                it.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    v.animate().scaleX(if (hasFocus) 1.04f else 1.0f).scaleY(if (hasFocus) 1.04f else 1.0f).setDuration(100).start()
                    if (v is MaterialButton) {
                        if (hasFocus) {
                            v.strokeWidth = 8
                            v.strokeColor = ColorStateList.valueOf(targetBorderColor)
                        } else {
                            v.strokeWidth = 0
                        }
                    }
                }
            }
        }
    }

    private fun triggerAppRestart() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            val packageManager = packageManager
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val componentName = intent?.component
            val mainIntent = Intent.makeRestartActivityTask(componentName)
            startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }, 300)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}