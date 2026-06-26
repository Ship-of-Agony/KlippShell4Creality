package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION", "Lint", "SetTextI18n", "LocalSuppress")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "SetTextI18n", "DefaultLocale", "NewApi")
class MainActivity : AppCompatActivity() {

    private fun toPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private lateinit var prefs: SharedPreferences
    private var autoStartExecuted = false

    private lateinit var containerPrinters: LinearLayout
    private lateinit var tvNoPrinter: TextView
    private lateinit var etMainPrinterName: EditText
    private lateinit var etMainPrinterIP: EditText
    private lateinit var etMainPrinterPort: EditText
    private lateinit var actvMainPrinterModel: AutoCompleteTextView
    private lateinit var headerAddPrinter: LinearLayout
    private lateinit var containerAddPrinterForm: LinearLayout
    private lateinit var tvAddPrinterTitle: TextView
    private lateinit var btnSystemSelect: MaterialButton

    private var selectedSystemIndex = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var shouldRecreateOnReturn = false

    // Genutzte Schnittstellen aus deinen angelegten Helpern
    private lateinit var otaUpdateManager: OtaUpdateManager
    private lateinit var networkScanHelper: NetworkScanHelper
    private lateinit var printerStorageHelper: PrinterStorageHelper

    private val printerMap = mapOf(
        "CR-10" to "cr_10", "CR-10 SE" to "cr_10se", "CR-10 Smart" to "cr_10smart",
        "CR-10 Smart Pro" to "cr_10smartpro", "CR-10S Pro V2" to "cr_10sprov2",
        "CR-20 Pro" to "cr_20pro", "CR-30" to "cr_30", "CR-6 SE" to "cr_6se",
        "CR-M4" to "cr_m4", "CR-M4 SE" to "cr_m4se", "Custom Printer" to "custem",
        "Ender 2 Pro" to "ender_2pro", "Ender 3" to "ender_3", "Ender 3 Max" to "ender_3max",
        "Ender 3 Max Neo" to "ender_3maxneo", "Ender 3 Neo" to "ender_3neo", "Ender 3 S1" to "ender_3s1",
        "Ender 3 S1 Plus" to "ender_3s1plus", "Ender 3 S1 Pro" to "ender_3s1pro", "Ender 3 V2" to "ender_3v2",
        "Ender 3 V3" to "ender_3v3", "Ender 3 V3 KE" to "ender_3v3ke", "Ender 3 V3 Plus" to "ender_3v3plus",
        "Ender 3 V3 SE" to "ender_3v3se", "Ender 4" to "ender_3v4", "Ender 5 Max" to "ender_5max",
        "Ender 5 Plus" to "ender_5plus", "Ender 5 S1" to "ender_5s1", "GS-01" to "gs_01",
        "GS-02" to "gs_02", "GS-03" to "gs_03", "GS-04" to "gs_04", "HI" to "hi",
        "K1" to "k1", "K1C" to "k1c", "K1 Max" to "k1max", "K1 SE" to "k1se",
        "K2" to "k2", "K2 Plus" to "k2plus", "K2 Pro" to "k2pro", "K2 SE" to "k2se",
        "Sermoon D3" to "sermoond3", "Sermoon D3 Pro" to "sermoond3pro", "Sermoon M300" to "sermoonm300",
        "Sermoon V1 Pro" to "sermoonv1pro", "Sonic Pad (Ender 3 S1)" to "sonic_ender_3s1",
        "Sonic Pad (Ender 5 S1)" to "sonic_ender_5s1", "Spark Xi7" to "sparkxi7"
    )

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) showCenteredPillToast(getString(R.string.autostart_disabled))
        checkAndRequestOverlayPermission()
    }

    private val requestOverlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        proceedWithAppInitialization()
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionRationaleDialog()
        } else {
            proceedWithAppInitialization()
        }
    }

    private fun isAndroidTV(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun getPrinterImageResource(modelName: String): Int {
        val mappedName = printerMap[modelName] ?: modelName.lowercase(Locale.getDefault()).replace(" ", "").replace("-", "_")
        val resourceName = "printer_$mappedName"
        val resId = resources.getIdentifier(resourceName, "drawable", packageName)
        return if (resId != 0) resId else R.mipmap.ic_launcher
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
            val errorChannelName = getString(R.string.channel_error_name)
            val infoChannelName = getString(R.string.channel_info_name)

            val errorChannel = NotificationChannel("klippshell_errors_channel", errorChannelName, AndroidNotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical printer errors and connection loss alerts"
                enableLights(true); lightColor = Color.RED; enableVibration(true)
            }
            val infoChannel = NotificationChannel("klippshell_info_channel", infoChannelName, AndroidNotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Printer progress updates and milestone completion alerts"
                enableLights(true); lightColor = Color.GREEN; enableVibration(true)
            }
            notificationManager.createNotificationChannel(errorChannel)
            notificationManager.createNotificationChannel(infoChannel)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "system") ?: "system"
        val config = Configuration(newBase.resources.configuration)

        if (savedLang != "system") {
            val locale = Locale(savedLang)
            Locale.setDefault(locale)
            config.setLocale(locale)
        }

        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            AppCompatDelegate.MODE_NIGHT_NO -> config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        }

        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        registerNotificationChannels()

        if (savedInstanceState != null) {
            shouldRecreateOnReturn = savedInstanceState.getBoolean("recreate_flag", false)
            autoStartExecuted = savedInstanceState.getBoolean("auto_start_executed", false)
        }

        super.onCreate(savedInstanceState)

        // Helper instanziieren
        printerStorageHelper = PrinterStorageHelper(prefs)
        networkScanHelper = NetworkScanHelper(lifecycleScope)
        otaUpdateManager = OtaUpdateManager(this, lifecycleScope, packageName, cacheDir, { title, items, hexColors, onSelected -> showPillDialog(title, items, hexColors, onSelected) }, { msg -> showCenteredPillToast(msg) })

        val printerArray = printerStorageHelper.getPrintersList()

        intent?.data?.let { uri ->
            if (uri.scheme == "klippshell" && uri.host == "open.printer") {
                if (printerArray.length() > 0) {
                    val primaryPrinter = printerArray.getJSONObject(0)
                    val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
                    val targetActivityClass = if (activeRole == "slave") CompanionRemoteActivity::class.java else WebViewActivity::class.java

                    startActivity(Intent(this, targetActivityClass).apply {
                        putExtra("PRINTER_IP", primaryPrinter.optString("ip", ""))
                        putExtra("PRINTER_PORT", primaryPrinter.optString("port", "7125"))
                        if (primaryPrinter.optString("defaultView", "") == "camera") putExtra("IS_CAMERA_VIEW", true)
                    })
                    finish(); return
                }
            }
        }

        setContentView(R.layout.activity_main)

        containerPrinters = findViewById(R.id.containerPrinters)
        tvNoPrinter = findViewById(R.id.tvNoPrinter)
        etMainPrinterName = findViewById(R.id.etMainPrinterName)
        etMainPrinterIP = findViewById(R.id.etMainPrinterIP)
        etMainPrinterPort = findViewById(R.id.etMainPrinterPort)
        actvMainPrinterModel = findViewById(R.id.actvMainPrinterModel)
        headerAddPrinter = findViewById(R.id.headerAddPrinter)
        containerAddPrinterForm = findViewById(R.id.containerAddPrinterForm)
        tvAddPrinterTitle = findViewById(R.id.tvAddPrinterTitle)
        btnSystemSelect = findViewById(R.id.btnSystemSelect)

        btnSystemSelect.text = "Port: 4408"
        etMainPrinterIP.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        etMainPrinterIP.keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.")

        val startupVeil = findViewById<LinearLayout?>(R.id.viewStartupVeil)
        startupVeil?.post {
            if (!prefs.getBoolean("has_shown_permissions", false)) {
                startupVeil.visibility = View.GONE; showPermissionRationaleDialog()
            } else {
                startupVeil.animate().translationY(-(startupVeil.height.toFloat())).alpha(0f).setStartDelay(500).setDuration(800).withEndAction {
                    startupVeil.visibility = View.GONE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        checkAndRequestOverlayPermission()
                    }
                }.start()
            }
        }

        findViewById<ImageView>(R.id.ivBackgroundWatermark)?.alpha = if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) 0.15f else 0.08f

        btnSystemSelect.setOnClickListener {
            showPillDialog(getString(R.string.choose_port), arrayOf(getString(R.string.system_creality), getString(R.string.system_standard), getString(R.string.system_manual))) { which ->
                selectedSystemIndex = which
                when (which) {
                    0 -> { btnSystemSelect.text = "Port: 4408"; etMainPrinterPort.visibility = View.GONE }
                    1 -> { btnSystemSelect.text = "Port: 7125"; etMainPrinterPort.visibility = View.GONE }
                    2 -> { btnSystemSelect.text = getString(R.string.system_manual); etMainPrinterPort.visibility = View.VISIBLE; etMainPrinterPort.requestFocus() }
                }
            }
        }

        val models = printerMap.keys.toTypedArray()
        actvMainPrinterModel.isFocusable = true; actvMainPrinterModel.isFocusableInTouchMode = false; actvMainPrinterModel.inputType = InputType.TYPE_NULL
        val openModelGridMenu = { showModelSelectionSearchDialog(getString(R.string.printer_model_hint), models) { actvMainPrinterModel.setText(it, false); etMainPrinterName.setText(it) } }
        actvMainPrinterModel.setOnClickListener { openModelGridMenu() }
        actvMainPrinterModel.setOnKeyListener { _, keyCode, event -> if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { openModelGridMenu(); true } else false }

        findViewById<View>(R.id.btnSettings)?.setOnClickListener { shouldRecreateOnReturn = true; startActivity(Intent(this, SettingsActivity::class.java)) }
        headerAddPrinter.setOnClickListener { val isVisible = containerAddPrinterForm.visibility == View.VISIBLE; containerAddPrinterForm.visibility = if (isVisible) View.GONE else View.VISIBLE; tvAddPrinterTitle.text = getString(if (isVisible) R.string.add_printer_down else R.string.add_printer_up); if (!isVisible) actvMainPrinterModel.requestFocus() }

        findViewById<View>(R.id.btnSearchNetwork)?.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
            dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.search_network)
            val progressBar = ProgressBar(this).apply {
                setPadding(0, toPx(24), 0, toPx(24))
                indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))
            }
            dialogView.findViewById<LinearLayout>(R.id.buttonContainer)?.addView(progressBar)
            val progressDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
            progressDialog.window?.setBackgroundDrawableResource(android.R.color.transparent); progressDialog.show()

            networkScanHelper.scanNetworkForPrinters { cleanList ->
                if (!isFinishing && !isDestroyed) {
                    try { progressDialog.dismiss() } catch (_: Exception) {}
                    if (cleanList.isNotEmpty()) {
                        val displayArray = cleanList.map { item -> val parts = item.split(":"); val port = parts.getOrNull(1) ?: "7125"; "${parts[0]} (${if (port == "4408") "Creality OS" else "Standard Klipper"})" }.toTypedArray()
                        showPillDialog(getString(R.string.found_printers), displayArray, null) { which -> val parts = cleanList[which].split(":"); etMainPrinterIP.setText(parts[0]); selectedSystemIndex = if (parts.getOrNull(1) == "4408") 0 else 1; btnSystemSelect.text = if (selectedSystemIndex == 0) "Port: 4408" else "Port: 7125"; etMainPrinterPort.visibility = View.GONE }
                    } else showCenteredPillToast(getString(R.string.toast_no_connection))
                }
            }
        }

        // BRUCHSICHER UND HOCHGRADIG RESPONSIV: Erkennt dynamisch den übergeordneten XML-Layout-Typen ohne abzustürzen
        val btnExit = findViewById<Button>(R.id.btnExitApp)
        btnExit?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935")); btnExit?.setTextColor(Color.WHITE)
        btnExit?.setOnClickListener { finishAffinity() }
        btnExit?.layoutParams?.let { params ->
            val isTvOrTablet = isAndroidTV() || (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
            params.width = if (!isTvOrTablet && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) toPx(260) else toPx(560)

            when (params) {
                is LinearLayout.LayoutParams -> params.gravity = Gravity.CENTER_HORIZONTAL
                is FrameLayout.LayoutParams -> params.gravity = Gravity.CENTER_HORIZONTAL
                is RelativeLayout.LayoutParams -> params.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE)
                is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams -> {
                    params.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    params.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                }
            }
            btnExit.layoutParams = params
        }

        findViewById<Button>(R.id.btnAddMainPrinter)?.setOnClickListener {
            val name = etMainPrinterName.text.toString().trim()
            val ip = etMainPrinterIP.text.toString().trim()
            val port = if (selectedSystemIndex == 0) "4408" else if (selectedSystemIndex == 1) "7125" else etMainPrinterPort.text.toString().trim().ifEmpty { "7125" }

            if (name.isNotEmpty() && ip.isNotEmpty()) {
                showPillDialog(getString(R.string.settings_role_title), arrayOf(getString(R.string.settings_role_auto), getString(R.string.settings_role_master), getString(R.string.settings_role_slave))) { selectedRoleIndex ->
                    prefs.edit().putString("app_device_role", when (selectedRoleIndex) { 1 -> "master"; 2 -> "slave"; else -> "auto" }).apply()
                    showPillDialog(getString(R.string.choose_default_view_title), arrayOf(getString(R.string.view_option_camera), getString(R.string.view_option_interface))) { whichView ->
                        printerStorageHelper.savePrinter(name, ip, port, actvMainPrinterModel.text.toString().trim().ifEmpty { getString(R.string.printer_model_default) }, if (whichView == 0) "camera" else "interface")
                        etMainPrinterName.text.clear(); etMainPrinterIP.text.clear(); etMainPrinterPort.text.clear(); actvMainPrinterModel.setText("", false)
                        selectedSystemIndex = 0; btnSystemSelect.text = "Port: 4408"; etMainPrinterPort.visibility = View.GONE; containerAddPrinterForm.visibility = View.GONE; tvAddPrinterTitle.text = getString(R.string.add_printer_down)
                        showCenteredPillToast(getString(R.string.printer_setup_success_format, name)); applyLanguageAndRefreshUI()
                    }
                }
            } else showPillDialog(getString(R.string.notify_title_error), arrayOf(getString(R.string.notify_btn_offline)), null) {}
        }

        arrayOf(findViewById(R.id.btnSettings), findViewById(R.id.btnSearchNetwork), findViewById<Button>(R.id.btnAddMainPrinter), findViewById(R.id.btnExitApp), headerAddPrinter, btnSystemSelect, etMainPrinterName, etMainPrinterIP, etMainPrinterPort, actvMainPrinterModel).forEach { btn ->
            btn?.isFocusable = true
            btn?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) { v.strokeWidth = if (hasFocus) 8 else 0; v.strokeColor = if (hasFocus) ColorStateList.valueOf(if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null }
            }
        }

        applyLanguageAndRefreshUI()
        if (prefs.getBoolean("update_auto_check", true)) otaUpdateManager.checkUpdatesSilentlyInBackground()
        if (printerArray.length() > 0) startTvBackgroundWorker()

        if (!autoStartExecuted && prefs.getBoolean("auto_start_printer", false) && printerArray.length() == 1) {
            autoStartExecuted = true; shouldRecreateOnReturn = true
            val primaryPrinter = printerArray.getJSONObject(0)
            val targetActivityClass = if ((prefs.getString("app_device_role", "auto") ?: "auto") == "slave") CompanionRemoteActivity::class.java else WebViewActivity::class.java
            startActivity(Intent(this, targetActivityClass).apply { putExtra("PRINTER_IP", primaryPrinter.optString("ip", "")); putExtra("PRINTER_PORT", primaryPrinter.optString("port", "7125")); if (primaryPrinter.optString("defaultView", "") == "camera") putExtra("IS_CAMERA_VIEW", true) })
        }
    }

    private fun proceedWithAppInitialization() {
        if (printerStorageHelper.getPrintersList().length() == 0) actvMainPrinterModel.requestFocus() else findViewById<View>(R.id.btnSettings)?.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("recreate_flag", shouldRecreateOnReturn); outState.putBoolean("auto_start_executed", autoStartExecuted)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (shouldRecreateOnReturn) { shouldRecreateOnReturn = false; recreate(); return }
        applyLanguageAndRefreshUI()
    }

    private fun startTvBackgroundWorker() {
        try {
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork("KlipperTvKachelWorker", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequest.Builder(KlipperTvWorker::class.java, 15, TimeUnit.MINUTES).build())
        } catch (e: Exception) { Log.e("KlippShell", "Error starting TV background worker", e) }
    }

    private fun createWelcomeTile(isNightMode: Boolean): View {
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card); elevation = 8f; setPadding(toPx(24), toPx(24), toPx(24), toPx(24)); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(toPx(16), toPx(12), toPx(16), toPx(12)) } }
        layout.addView(TextView(this).apply { text = try { getString(resources.getIdentifier("welcome_tile_title", "string", packageName)) } catch(_: Exception) { getString(R.string.widget_printer_title) }; textSize = 20f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(textColor); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = toPx(8) } })
        layout.addView(TextView(this).apply { text = try { getString(resources.getIdentifier("welcome_tile_msg", "string", packageName)) } catch(_: Exception) { "..." }; textSize = 15f; setTextColor(textColor); alpha = 0.85f; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = toPx(20) } })
        layout.addView(MaterialButton(this).apply {
            text = try { getString(resources.getIdentifier("welcome_tile_btn", "string", packageName)) } catch(_: Exception) { getString(R.string.btn_later) }; isAllCaps = false; textSize = 16f; setPadding(0, toPx(14), 0, toPx(14)); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")); setTextColor(Color.WHITE); shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel.builder().setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, 100f).build(); isFocusable = true
            onFocusChangeListener = View.OnFocusChangeListener { v, hF -> v.animate().scaleX(if (hF) 1.03f else 1.0f).scaleY(if (hF) 1.03f else 1.0f).setDuration(150).start(); if (v is MaterialButton) { v.strokeWidth = if (hF) 8 else 0; v.strokeColor = if (hF) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null } }
            setOnClickListener { prefs.edit().putBoolean("has_seen_welcome_tile", true).apply(); loadPrinters() }
        })
        return layout
    }

    private fun loadPrinters() {
        containerPrinters.removeAllViews()
        val list = printerStorageHelper.getPrintersList()
        val hasSeenWelcome = prefs.getBoolean("has_seen_welcome_tile", false)
        tvNoPrinter.visibility = if (list.length() == 0 && hasSeenWelcome) View.VISIBLE else View.GONE

        // Das Formular öffnet sich bei leerer Liste NUR, wenn das Willkommens-Kachel-Widget bereits geschlossen wurde
        if (list.length() == 0 && hasSeenWelcome) {
            containerAddPrinterForm.isVisible = true
            tvAddPrinterTitle.text = getString(R.string.add_printer_up)
            WorkManager.getInstance(applicationContext).cancelUniqueWork("KlipperTvKachelWorker")
        } else if (list.length() == 0 && !hasSeenWelcome) {
            containerAddPrinterForm.isVisible = false
            tvAddPrinterTitle.text = getString(R.string.add_printer_down)
        }

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val useHorizontalLayout = list.length() <= 2 && hasSeenWelcome
        containerPrinters.orientation = if (useHorizontalLayout) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        containerPrinters.gravity = if (useHorizontalLayout) Gravity.CENTER else Gravity.NO_GRAVITY

        if (!hasSeenWelcome) containerPrinters.addView(createWelcomeTile(isNightMode))

        for (i in 0 until list.length()) {
            val printer = list.optJSONObject(i) ?: continue
            val itemView = LayoutInflater.from(this).inflate(R.layout.printer_item, containerPrinters, false).apply { isFocusable = true }
            itemView.layoutParams = LinearLayout.LayoutParams(if (useHorizontalLayout) toPx(280) else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(toPx(16), toPx(12), toPx(16), toPx(12)); if (useHorizontalLayout) gravity = Gravity.CENTER_VERTICAL }
            if (useHorizontalLayout && itemView is LinearLayout) { itemView.orientation = LinearLayout.VERTICAL; itemView.gravity = Gravity.CENTER; itemView.setPadding(toPx(24), toPx(32), toPx(24), toPx(32)) }

            val tvName = itemView.findViewById<TextView>(R.id.tvPrinterNameAndAddress)
            itemView.onFocusChangeListener = View.OnFocusChangeListener { v, hF ->
                val drawable = v.background as? GradientDrawable
                if (hF) { v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(8f).setDuration(150).start(); v.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF")); drawable?.setStroke(8, if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")); if (isNightMode) tvName?.setTextColor(Color.WHITE) }
                else { v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start(); v.backgroundTintList = null; drawable?.setStroke(2, if (isNightMode) Color.parseColor("#4DFFFFFF") else Color.parseColor("#33000000")); tvName?.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK) }
            }

            itemView.findViewById<ImageView>(R.id.ivPrinterIcon)?.let { icon -> icon.setImageResource(getPrinterImageResource(printer.optString("model", ""))); if (useHorizontalLayout) icon.layoutParams = LinearLayout.LayoutParams(toPx(90), toPx(90)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, toPx(16)) } }
            tvName?.let { tv -> tv.text = printer.optString("name", getString(R.string.widget_printer_unknown)); tv.textSize = if (useHorizontalLayout) 20f else 18f; tv.setTypeface(null, android.graphics.Typeface.BOLD); tv.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK); if (useHorizontalLayout) tv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL } }

            itemView.setOnClickListener {
                shouldRecreateOnReturn = true
                val targetActivityClass = if ((prefs.getString("app_device_role", "auto") ?: "auto") == "slave") CompanionRemoteActivity::class.java else WebViewActivity::class.java
                startActivity(Intent(this, targetActivityClass).apply { putExtra("PRINTER_IP", printer.optString("ip", "")); putExtra("PRINTER_PORT", printer.optString("port", "7125")); if (printer.optString("defaultView", "") == "camera") putExtra("IS_CAMERA_VIEW", true) })
            }

            itemView.setOnLongClickListener {
                showPillDialog(printer.optString("name", getString(R.string.printer_default_label)), arrayOf(getString(R.string.choose_default_view_title), getString(R.string.yes_delete)), arrayOf(null, "#E53935")) { action ->
                    if (action == 0) {
                        showPillDialog(getString(R.string.choose_default_view_title), arrayOf(getString(R.string.view_option_camera), getString(R.string.view_option_interface))) { whichView ->
                            printerStorageHelper.updatePrinterView(i, if (whichView == 0) "camera" else "interface")
                            applyLanguageAndRefreshUI(); showCenteredPillToast(getString(R.string.toast_view_changed_success))
                        }
                    } else {
                        showPillDialog(getString(R.string.reset_confirm_msg), arrayOf(getString(R.string.yes_delete), getString(R.string.cancel)), arrayOf("#E53935", null)) { confirm ->
                            if (confirm == 0) { printerStorageHelper.deletePrinter(i); applyLanguageAndRefreshUI() }
                        }
                    }
                }
                true
            }
            containerPrinters.addView(itemView)
        }

        // ANTI-KOLLAPS PROTECTOR: Verhindert das Hochschießen des Beenden-Buttons bei einer leeren Liste
        val btnExit = findViewById<Button>(R.id.btnExitApp)
        btnExit?.layoutParams?.let { currentParams ->
            if (currentParams is LinearLayout.LayoutParams) {
                if (list.length() == 0 && !hasSeenWelcome) {
                    currentParams.topMargin = toPx(280) // Drückt den Button im leeren Zustand sauber nach unten
                } else {
                    currentParams.topMargin = toPx(24)
                }
                btnExit.layoutParams = currentParams
            }
        }
    }

    private fun applyLanguageAndRefreshUI() {
        tvAddPrinterTitle.text = getString(if (containerAddPrinterForm.visibility == View.VISIBLE) R.string.add_printer_up else R.string.add_printer_down)
        tvNoPrinter.text = getString(R.string.no_printers)
        findViewById<Button>(R.id.btnSearchNetwork)?.text = getString(R.string.btn_search_network)
        findViewById<Button>(R.id.btnAddMainPrinter)?.text = getString(R.string.btn_add)
        findViewById<Button>(R.id.btnExitApp)?.text = getString(R.string.btn_exit)

        etMainPrinterName.hint = getString(R.string.printer_name_hint); etMainPrinterIP.hint = getString(R.string.printer_ip_hint)
        etMainPrinterPort.hint = getString(R.string.printer_port_hint); actvMainPrinterModel.hint = getString(R.string.printer_model_hint)

        when (selectedSystemIndex) {
            0 -> btnSystemSelect.text = "Port: 4408"
            1 -> btnSystemSelect.text = "Port: 7125"
            2 -> btnSystemSelect.text = getString(R.string.system_manual)
        }
        loadPrinters()
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val backgroundColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val textColorResource = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val pillView = TextView(this).apply {
            text = message; textSize = 16f; gravity = Gravity.CENTER; setTextColor(textColorResource)
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 100f; setColor(backgroundColor); setStroke(4, textColorResource) }
            setPadding(toPx(50), toPx(35), toPx(50), toPx(35))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setMargins(toPx(50), 0, toPx(50), toPx(120)) }
        }
        val container = FrameLayout(this).apply { addView(pillView) }
        rootLayout.addView(container)
        mainHandler.postDelayed({ if (!isFinishing && !isDestroyed) rootLayout.removeView(container) }, 2200)
    }

    private fun showModelSelectionSearchDialog(title: String, allModels: Array<String>, onSelected: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val mainContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cardBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val textColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val etSearch = EditText(this).apply {
            hint = getString(R.string.search_model_hint); background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_pill_input)
            setPadding(toPx(24), toPx(16), toPx(24), toPx(16)); textSize = 15f; setTextColor(textColor); setHintTextColor(if (isNightMode) Color.GRAY else Color.parseColor("#757575"))
            isFocusable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, toPx(16)) }
        }
        mainContainer.addView(etSearch)

        val scrollLayout = ScrollView(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(320)).apply { clipToPadding = false; clipChildren = false }; isVerticalScrollBarEnabled = true }
        val gridLayout = GridLayout(this).apply { columnCount = 2; alignmentMode = GridLayout.ALIGN_BOUNDS; isRowOrderPreserved = false; layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { clipToPadding = false; clipChildren = false } }
        scrollLayout.addView(gridLayout); mainContainer.addView(scrollLayout)

        fun populateList(filterText: String) {
            gridLayout.removeAllViews()
            allModels.filter { it.lowercase(Locale.getDefault()).contains(filterText.lowercase(Locale.getDefault())) }.forEach { modelName ->
                val kachel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isFocusable = true; isClickable = true
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded); backgroundTintList = ColorStateList.valueOf(cardBgColor); setPadding(toPx(16), toPx(20), toPx(16), toPx(20))
                    layoutParams = GridLayout.LayoutParams().apply { width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(toPx(8), toPx(8), toPx(8), toPx(8)) }
                    onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                        v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(120).start()
                        (v.background as? GradientDrawable)?.setStroke(6, if (hasFocus) (if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else (if (isNightMode) Color.parseColor("#4DFFFFFF") else Color.parseColor("#33000000")))
                    }
                    setOnClickListener { onSelected(modelName); dialog.dismiss() }
                }
                kachel.addView(ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(toPx(64), toPx(64)).apply { setMargins(0, 0, 0, toPx(8)) }; scaleType = ImageView.ScaleType.CENTER_INSIDE; setImageResource(getPrinterImageResource(modelName)) })
                kachel.addView(TextView(this).apply { text = modelName; textSize = 14f; gravity = Gravity.CENTER; setTextColor(textColor); setTypeface(null, android.graphics.Typeface.BOLD); maxLines = 2 })
                gridLayout.addView(kachel)
            }
        }

        populateList("")
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { populateList(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.show(); etSearch.requestFocus()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun showPillDialog(title: String, items: Array<String>, hexColors: Array<String?>? = null, onSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pVert = if (isAndroidTV() || (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE) toPx(30) else toPx(14)

        items.forEachIndexed { index, itemText ->
            val btn = MaterialButton(this).apply {
                text = itemText; isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, pVert, 0, pVert)
                val customHex = hexColors?.getOrNull(index)
                if (customHex != null) { backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex)); setTextColor(Color.WHITE) }
                else { backgroundTintList = ColorStateList.valueOf(if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")); setTextColor(if (isNightMode) Color.WHITE else Color.BLACK) }
                isFocusable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, toPx(10), 0, toPx(10)) }
                onFocusChangeListener = View.OnFocusChangeListener { v, hF ->
                    if (hF) { v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 8; v.strokeColor = ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) }
                    else { v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
                }
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }
            container?.addView(btn)
        }
        dialog.show()
    }

    private fun showPermissionRationaleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setCancelable(false).setView(dialogView).create().apply { setCanceledOnTouchOutside(false); setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK } }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.perm_dialog_title)
        val mainContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        mainContainer?.addView(TextView(this).apply { text = getString(R.string.perm_dialog_msg); textSize = 15f; setTextColor(if (isNightMode) Color.WHITE else Color.BLACK); setPadding(toPx(24), toPx(16), toPx(24), toPx(32)); gravity = Gravity.START }, 0)

        val pVert = if (isAndroidTV() || (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE) toPx(30) else toPx(14)

        val btnAccept = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn); isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, pVert, 0, pVert); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")); setTextColor(Color.WHITE); isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, toPx(16)) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hF -> v.animate().scaleX(if (hF) 1.04f else 1.0f).scaleY(if (hF) 1.04f else 1.0f).translationZ(if (hF) 6f else 0f).setDuration(100).start(); if (v is MaterialButton) { v.strokeWidth = if (hF) 8 else 0; v.strokeColor = if (hF) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null } }
            setOnClickListener { prefs.edit().putBoolean("has_shown_permissions", true).apply(); dialog.dismiss(); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) else checkAndRequestOverlayPermission() }
        }

        val btnDecline = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn_decline); isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, pVert, 0, pVert); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935")); setTextColor(Color.WHITE); isFocusable = true
            onFocusChangeListener = View.OnFocusChangeListener { v, hF -> v.animate().scaleX(if (hF) 1.04f else 1.0f).scaleY(if (hF) 1.04f else 1.0f).translationZ(if (hF) 6f else 0f).setDuration(100).start(); if (v is MaterialButton) { v.strokeWidth = if (hF) 8 else 0; v.strokeColor = if (hF) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null } }
            setOnClickListener { dialog.dismiss(); finishAffinity() }
        }

        mainContainer?.addView(btnAccept); mainContainer?.addView(btnDecline)
        dialog.show(); btnAccept.requestFocus()
    }

    private fun showOverlayPermissionRationaleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setCancelable(false).setView(dialogView).create().apply { setCanceledOnTouchOutside(false); setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK } }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.overlay_permission_title)
        val mainContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        mainContainer?.addView(TextView(this).apply { text = getString(R.string.overlay_permission_desc); textSize = 15f; setTextColor(if (isNightMode) Color.WHITE else Color.BLACK); setPadding(toPx(24), toPx(16), toPx(24), toPx(32)); gravity = Gravity.START }, 0)

        val pVert = if (isAndroidTV() || (resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE) toPx(30) else toPx(14)

        val btnAccept = MaterialButton(this).apply {
            text = getString(R.string.overlay_permission_btn); isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, pVert, 0, pVert); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")); setTextColor(Color.WHITE); isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, toPx(16)) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hF -> v.animate().scaleX(if (hF) 1.04f else 1.0f).scaleY(if (hF) 1.04f else 1.0f).translationZ(if (hF) 6f else 0f).setDuration(100).start(); if (v is MaterialButton) { v.strokeWidth = if (hF) 8 else 0; v.strokeColor = if (hF) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null } }
            setOnClickListener {
                dialog.dismiss()
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    requestOverlayPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                        requestOverlayPermissionLauncher.launch(fallbackIntent)
                    } catch (ex: Exception) {
                        showCenteredPillToast("System-Menü konnte nicht geöffnet werden.")
                        proceedWithAppInitialization()
                    }
                }
            }
        }

        val btnDecline = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn_decline); isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, pVert, 0, pVert); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935")); setTextColor(Color.WHITE); isFocusable = true
            onFocusChangeListener = View.OnFocusChangeListener { v, hF -> v.animate().scaleX(if (hF) 1.04f else 1.0f).scaleY(if (hF) 1.04f else 1.0f).translationZ(if (hF) 6f else 0f).setDuration(100).start(); if (v is MaterialButton) { v.strokeWidth = if (hF) 8 else 0; v.strokeColor = if (hF) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null } }
            setOnClickListener { dialog.dismiss(); proceedWithAppInitialization() }
        }

        mainContainer?.addView(btnAccept); mainContainer?.addView(btnDecline)
        dialog.show(); btnAccept.requestFocus()
    }

    override fun onDestroy() { mainHandler.removeCallbacksAndMessages(null); currentFocus?.clearFocus(); super.onDestroy() }
}