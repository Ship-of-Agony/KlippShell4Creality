package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONException
import org.json.JSONObject

@Suppress("DEPRECATION", "Lint", "SetTextI18n", "LocalSuppress")
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "SetTextI18n", "DefaultLocale", "NewApi")
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // Kontrollvariable gegen Endlosschleifen beim Zurückgehen
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

    private val printerMap = mapOf(
        "CR-10" to "cr_10", "CR-10 SE" to "cr_10se", "CR-10 Smart" to "cr_10smart",
        "CR-10 Smart Pro" to "cr_10smartpro", "CR-10S Pro V2" to "cr_10sprov2",
        "CR-20 Pro" to "cr_20pro", "CR-30" to "cr_30", "CR-6 SE" to "cr_6se",
        "CR-M4" to "cr_m4", "CR-M4 SE" to "cr_m4se", "Custom Printer" to "custem",
        "Ender 2 Pro" to "ender_2pro", "Ender 3" to "ender_3", "Ender 3 Max" to "ender_3max",
        "Ender 3 Max Neo" to "ender_3maxneo", "Ender 3 Neo" to "ender_3neo", "Ender 3 S1" to "ender_3s1",
        "Ender 3 S1 Plus" to "ender_3s1plus", "Ender 3 S1 Pro" to "ender_3s1pro", "Ender 3 V2" to "ender_3v2",
        "Ender 3 V3" to "ender_3v3", "Ender 3 V3 KE" to "ender_3v3ke", "Ender 3 V3 Plus" to "ender_3v3plus",
        "Ender 3 V3 SE" to "ender_3v3se", "Ender 4" to "ender_4", "Ender 5 Max" to "ender_5max",
        "Ender 5 Plus" to "ender_5plus", "Ender 5 S1" to "ender_5s1", "GS-01" to "gs_01",
        "GS-02" to "gs_02", "GS-03" to "gs_03", "GS-04" to "gs_04", "HI" to "hi",
        "K1" to "k1", "K1C" to "k1c", "K1 Max" to "k1max", "K1 SE" to "k1se",
        "K2" to "k2", "K2 Plus" to "k2plus", "K2 Pro" to "k2pro", "K2 SE" to "k2se",
        "Sermoon D3" to "sermoond3", "Sermoon D3 Pro" to "sermoond3pro", "Sermoon M300" to "sermoonm300",
        "Sermoon V1 Pro" to "sermoonv1pro", "Sonic Pad (Ender 3 S1)" to "sonic_ender_3s1",
        "Sonic Pad (Ender 5 S1)" to "sonic_ender_5s1", "Spark Xi7" to "sparkxi7"
    )

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

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedLang = prefs.getString("app_lang", "system") ?: "system"
        val config = android.content.res.Configuration(newBase.resources.configuration)

        if (savedLang != "system") {
            val locale = Locale(savedLang)
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
        prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        if (savedInstanceState != null) {
            shouldRecreateOnReturn = savedInstanceState.getBoolean("recreate_flag", false)
            autoStartExecuted = savedInstanceState.getBoolean("auto_start_executed", false)
        }

        super.onCreate(savedInstanceState)

        intent?.data?.let { uri ->
            if (uri.scheme == "klippshell" && uri.host == "open.printer") {
                val printerArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (_: Exception) { JSONArray() }
                if (printerArray.length() > 0) {
                    val primaryPrinter = printerArray.getJSONObject(0)

                    val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
                    val targetActivityClass = if (activeRole == "slave") CompanionRemoteActivity::class.java else WebViewActivity::class.java

                    val webViewIntent = Intent(this, targetActivityClass).apply {
                        putExtra("PRINTER_IP", primaryPrinter.optString("ip", ""))
                        putExtra("PRINTER_PORT", primaryPrinter.optString("port", "7125"))
                        if (primaryPrinter.optString("defaultView", "") == "camera") {
                            putExtra("IS_CAMERA_VIEW", true)
                        }
                    }
                    startActivity(webViewIntent)
                    finish()
                    return
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

        val printerArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }

        val startupVeil = findViewById<LinearLayout?>(R.id.viewStartupVeil)
        startupVeil?.post {
            if (!prefs.getBoolean("has_shown_permissions", false)) {
                startupVeil.visibility = View.GONE
                showPermissionRationaleDialog()
            } else {
                startupVeil.animate()
                    .translationY(-(startupVeil.height.toFloat()))
                    .alpha(0f)
                    .setStartDelay(500)
                    .setDuration(800)
                    .withEndAction {
                        startupVeil.visibility = View.GONE
                        if (printerArray.length() == 0) actvMainPrinterModel.requestFocus() else findViewById<View>(R.id.btnSettings)?.requestFocus()
                    }
                    .start()
            }
        }

        val ivBackgroundWatermark = findViewById<ImageView>(R.id.ivBackgroundWatermark)
        ivBackgroundWatermark?.alpha = if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) 0.15f else 0.08f

        btnSystemSelect.setOnClickListener {
            val systemOptions = arrayOf(getString(R.string.system_creality), getString(R.string.system_standard), getString(R.string.system_manual))
            showPillDialog(getString(R.string.choose_port), systemOptions) { which ->
                selectedSystemIndex = which
                when (which) {
                    0 -> { btnSystemSelect.text = "Port: 4408"; etMainPrinterPort.visibility = View.GONE }
                    1 -> { btnSystemSelect.text = "Port: 7125"; etMainPrinterPort.visibility = View.GONE }
                    2 -> { btnSystemSelect.text = getString(R.string.system_manual); etMainPrinterPort.visibility = View.VISIBLE; etMainPrinterPort.requestFocus() }
                }
            }
        }

        val models = printerMap.keys.toTypedArray()
        actvMainPrinterModel.isFocusable = true
        actvMainPrinterModel.isFocusableInTouchMode = false
        actvMainPrinterModel.inputType = InputType.TYPE_NULL

        val openModelGridMenu = {
            showModelSelectionSearchDialog(getString(R.string.printer_model_hint), models) { selectedModel ->
                actvMainPrinterModel.setText(selectedModel, false)
                etMainPrinterName.setText(selectedModel)
            }
        }
        actvMainPrinterModel.setOnClickListener { openModelGridMenu() }
        actvMainPrinterModel.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) { openModelGridMenu(); true } else false
        }
        actvMainPrinterModel.setText("", false)

        findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            shouldRecreateOnReturn = true
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        headerAddPrinter.setOnClickListener {
            val isVisible = containerAddPrinterForm.visibility == View.VISIBLE
            containerAddPrinterForm.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvAddPrinterTitle.text = getString(if (isVisible) R.string.add_printer_down else R.string.add_printer_up)
            if (!isVisible) actvMainPrinterModel.requestFocus()
        }

        findViewById<Button>(R.id.btnSearchNetwork)?.setOnClickListener { searchNetworkForPrinters() }

        val btnExit = findViewById<Button>(R.id.btnExitApp)
        btnExit?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
        btnExit?.setTextColor(Color.WHITE)
        btnExit?.setOnClickListener { finishAffinity() }
        btnExit?.layoutParams?.let { params ->
            params.width = toPx(560)
            if (params is LinearLayout.LayoutParams) {
                params.gravity = Gravity.CENTER_HORIZONTAL
            } else if (params is FrameLayout.LayoutParams) {
                params.gravity = Gravity.CENTER_HORIZONTAL
            } else if (params.javaClass.name.contains("ConstraintLayout")) {
                try {
                    val clazz = params.javaClass
                    clazz.getField("matchConstraintDefaultWidth").set(params, 0)
                    clazz.getField("startToStart").set(params, 0)
                    clazz.getField("endToEnd").set(params, 0)
                    clazz.getField("leftToLeft").set(params, 0)
                    clazz.getField("rightToRight").set(params, 0)
                } catch (e: Exception) {
                    Log.e("KlippShell", "Fehler beim Zentrieren des Beenden-Buttons", e)
                }
            }
            btnExit.layoutParams = params
        }

        findViewById<Button>(R.id.btnAddMainPrinter)?.setOnClickListener {
            val name = etMainPrinterName.text.toString().trim()
            val ip = etMainPrinterIP.text.toString().trim()
            val port = when (selectedSystemIndex) {
                0 -> "4408"
                1 -> "7125"
                else -> etMainPrinterPort.text.toString().trim().ifEmpty { "7125" }
            }

            if (name.isNotEmpty() && ip.isNotEmpty()) {
                val viewOptions = arrayOf(getString(R.string.menu_change_camera_type), getString(R.string.choose_default_view))
                showPillDialog(getString(R.string.choose_default_view_title), viewOptions) { which ->
                    val modelText = actvMainPrinterModel.text.toString().trim().ifEmpty { "Standard Drucker" }
                    savePrinter(name, ip, port, modelText, if (which == 0) "camera" else "interface")

                    etMainPrinterName.text.clear()
                    etMainPrinterIP.text.clear()
                    etMainPrinterPort.text.clear()
                    actvMainPrinterModel.setText("", false)

                    selectedSystemIndex = 0
                    btnSystemSelect.text = "Port: 4408"
                    etMainPrinterPort.visibility = View.GONE
                    containerAddPrinterForm.visibility = View.GONE
                    tvAddPrinterTitle.text = getString(R.string.add_printer_down)
                    showCenteredPillToast(getString(R.string.choose_default_view_title) + " ✓")
                }
            } else {
                showPillDialog(getString(R.string.notify_title_error), arrayOf(getString(R.string.notify_btn_offline)), null) { }
            }
        }

        val buttons = arrayOf(
            findViewById(R.id.btnSettings), findViewById(R.id.btnSearchNetwork),
            findViewById<Button>(R.id.btnAddMainPrinter), findViewById(R.id.btnExitApp),
            headerAddPrinter, btnSystemSelect, etMainPrinterName, etMainPrinterIP,
            etMainPrinterPort, actvMainPrinterModel
        )

        buttons.forEach { btn ->
            btn?.isFocusable = true
            btn?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }
        }

        applyLanguageAndRefreshUI()

        if (prefs.getBoolean("update_auto_check", true)) checkUpdatesSilentlyInBackground()
        if (printerArray.length() > 0) startTvBackgroundWorker()

        if (!autoStartExecuted && prefs.getBoolean("auto_start_printer", false) && printerArray.length() == 1) {
            autoStartExecuted = true
            val primaryPrinter = printerArray.getJSONObject(0)
            shouldRecreateOnReturn = true

            val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
            val targetActivityClass = if (activeRole == "slave") CompanionRemoteActivity::class.java else WebViewActivity::class.java

            startActivity(Intent(this, targetActivityClass).apply {
                putExtra("PRINTER_IP", primaryPrinter.optString("ip", ""))
                putExtra("PRINTER_PORT", primaryPrinter.optString("port", "7125"))
                if (primaryPrinter.optString("defaultView", "") == "camera") {
                    putExtra("IS_CAMERA_VIEW", true)
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("recreate_flag", shouldRecreateOnReturn)
        outState.putBoolean("auto_start_executed", autoStartExecuted)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (shouldRecreateOnReturn) {
            shouldRecreateOnReturn = false
            recreate()
            return
        }
        applyLanguageAndRefreshUI()
    }

    private fun startTvBackgroundWorker() {
        try {
            val tvWorkRequest = PeriodicWorkRequest.Builder(KlipperTvWorker::class.java, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "KlipperTvKachelWorker", ExistingPeriodicWorkPolicy.KEEP, tvWorkRequest
            )
        } catch (e: Exception) { Log.e("KlippShell", "Fehler beim Starten des TV-Workers", e) }
    }

    private fun createWelcomeTile(isNightMode: Boolean): View {
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(toPx(16), toPx(12), toPx(16), toPx(12))
            }
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card)
            elevation = 8f
            setPadding(toPx(24), toPx(24), toPx(24), toPx(24))
        }

        val title = TextView(this).apply {
            text = try { getString(resources.getIdentifier("welcome_tile_title", "string", packageName)) } catch(e: Exception) { "Willkommen bei KlippShell! 👋" }
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = toPx(8)
            }
        }

        val msg = TextView(this).apply {
            text = try { getString(resources.getIdentifier("welcome_tile_msg", "string", packageName)) } catch(e: Exception) { "Schön, dass du da bist! Damit du das Beste aus deinem Drucker-Monitoring herausholen kannst, ist hier eine kurze Übersicht zu den Kernfunktionen von KlippShell und den Besonderheiten einiger TV-Geräte. KlippShell wurde so konzipiert, dass es auf dem Smartphone, dem Tablet oder bedienerfreundlich auf dem Android TV läuft.\n\n1️⃣ ERSTE SCHRITTE\nFüge unten deinen ersten 3D-Drucker hinzu. Aktuell sind fast alle auf dem Markt befindlichen Modelle eingepflegt. Falls du einen anderen Drucker auf Klipper-Basis nutzen möchtest, kannst du \"Custom Model\" wählen.\nHinweis: Je nach Druckermodell und Netzwerk-Konfiguration musst du eventuell deine Ports und Kamera-Zugänge manuell anpassen, um ein einwandfreies Monitoring zu garantieren. Der automatische Netzwerkscan sollte jedoch die meisten Konfigurationen selbstständig abdecken.\n⚠️ Falls du kein Livebild des Druckers bekommst, schau bitte unter https://github.com/DnG-Crafts/K2-Camera vorbei und installiere den FIX. Dies erfordert leider Root-Rechte auf dem Drucker!\n💡 Tipp: Ein langer Tastendruck im Hauptmenü auf einen deiner gespeicherten Drucker öffnet die Optionen, um die Standardansicht zwischen Live-Stream Monitoring und Klipper OS zu ändern oder diesen Drucker zu löschen.\n\n2️⃣ SMARTE LIVE-STEUERUNG\nIm Videostream-Modus steuerst du alles über drei untere Haupttasten:\n• Linke Taste: Öffnet das Menü für On-Screen-Display (OSD), Video-Zoom, PiP, Gehäuse-Beleuchtung, Bildschirmschoner und Bildformat.\n• Mittlere Taste: Wechselt nahtlos zwischen Klipper OS (Dashboard) und dem reinen Kamera-Livestream.\n• Rechte Taste: Bringt dich jederzeit sicher zum Hauptmenü zurück.\n\n3️⃣ DIREKTSTART-MODUS\nWenn du einen Drucker bereits erfolgreich konfiguriert hast, kannst du in den Einstellungen den Direktstart-Modus wählen. Dieser erlaubt dir, direkt beim App-Start in der Live-Stream-Ansicht zu beginnen.\n\n4️⃣ DER COMPANION-MODUS\nMit KlippShell kannst du dein Gerät als TV-Dashboard (Master) oder als Smartphone-Fernbedienung (Slave) nutzen. Aktiviere den Companion-Modus in den Einstellungen, um deinen Fernseher komfortabel von deinem Smartphone aus zu steuern.\n\n5️⃣ PICTURE-IN-PICTURE (PiP)\nObwohl KlippShell PiP vollständig unterstützt, weigern sich einige TV-Hersteller, dieses Feature nativ anzubieten. Keine Sorge: In den Einstellungen findest du eine erweiterte Funktion mit einer präzisen Anleitung, wie du PiP auf deinem TV-Gerät ganz einfach via ADB-Befehl freischalten kannst!\n\n6️⃣ TV \"NOW LIVE\" FUNKTION\nWenn du die \"Now Live\" TV-Funktion verwenden möchtest, kann es durch Hersteller-Restriktionen vorkommen, dass diese nicht nativ auf deinem TV-Dashboard angezeigt werden. In diesem Fall empfehlen wir, einen alternativen TV-Launcher wie den \"Projectivity Launcher\" auf dem TV-Gerät zu nutzen, um in den vollen Genuss dieser Funktion zu kommen." }
            textSize = 15f
            setTextColor(textColor)
            alpha = 0.85f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = toPx(20)
            }
        }

        val btnDismiss = MaterialButton(this).apply {
            text = try { getString(resources.getIdentifier("welcome_tile_btn", "string", packageName)) } catch(e: Exception) { "Verstanden & Ausblenden" }
            isAllCaps = false
            textSize = 16f
            setPadding(0, toPx(14), 0, toPx(14))
            shapeAppearanceModel = com.google.android.material.shape.ShapeAppearanceModel.builder().setAllCorners(com.google.android.material.shape.CornerFamily.ROUNDED, 100f).build()
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            isFocusable = true
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }

            setOnClickListener {
                prefs.edit().putBoolean("has_seen_welcome_tile", true).apply()
                loadPrinters()
            }
        }

        layout.addView(title)
        layout.addView(msg)
        layout.addView(btnDismiss)

        return layout
    }

    private fun loadPrinters() {
        containerPrinters.removeAllViews()
        val list = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { prefs.edit().putString("printers_list", "[]").apply(); JSONArray() }

        val hasSeenWelcome = prefs.getBoolean("has_seen_welcome_tile", false)
        tvNoPrinter.visibility = if (list.length() == 0 && hasSeenWelcome) View.VISIBLE else View.GONE

        if (list.length() == 0) {
            containerAddPrinterForm.isVisible = true; tvAddPrinterTitle.text = getString(R.string.add_printer_up)
            WorkManager.getInstance(applicationContext).cancelUniqueWork("KlipperTvKachelWorker")
        }

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val useHorizontalLayout = list.length() <= 2 && hasSeenWelcome
        containerPrinters.orientation = if (useHorizontalLayout) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        containerPrinters.gravity = if (useHorizontalLayout) Gravity.CENTER else Gravity.NO_GRAVITY

        if (!hasSeenWelcome) {
            containerPrinters.addView(createWelcomeTile(isNightMode))
        }

        for (i in 0 until list.length()) {
            val printer = try { list.getJSONObject(i) } catch (_: Exception) { null } ?: continue
            val itemView = LayoutInflater.from(this).inflate(R.layout.printer_item, containerPrinters, false).apply { isFocusable = true; isFocusableInTouchMode = false }

            itemView.layoutParams = LinearLayout.LayoutParams(if (useHorizontalLayout) toPx(280) else ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(toPx(16), toPx(12), toPx(16), toPx(12)); if (useHorizontalLayout) gravity = Gravity.CENTER_VERTICAL
            }

            if (useHorizontalLayout && itemView is LinearLayout) { itemView.orientation = LinearLayout.VERTICAL; itemView.gravity = Gravity.CENTER; itemView.setPadding(toPx(24), toPx(32), toPx(24), toPx(32)) }
            val tvName = itemView.findViewById<TextView>(R.id.tvPrinterNameAndAddress)

            itemView.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                val drawable = v.background as? GradientDrawable
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(8f).setDuration(150).start(); v.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
                    drawable?.setStroke(8, if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    if (isNightMode) tvName?.setTextColor(Color.WHITE)
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start(); v.backgroundTintList = null
                    drawable?.setStroke(2, if (isNightMode) Color.parseColor("#4DFFFFFF") else Color.parseColor("#33000000"))
                    tvName?.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
                }
            }

            itemView.findViewById<ImageView>(R.id.ivPrinterIcon)?.let { icon ->
                icon.setImageResource(getPrinterImageResource(printer.optString("model", "")))
                if (useHorizontalLayout) icon.layoutParams = LinearLayout.LayoutParams(toPx(90), toPx(90)).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, 0, 0, toPx(16)) }
            }

            tvName?.let { tv ->
                tv.text = printer.optString("name", "Unbekannt"); tv.textSize = if (useHorizontalLayout) 20f else 18f; tv.setTypeface(null, android.graphics.Typeface.BOLD)
                tv.setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
                if (useHorizontalLayout) tv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            }

            itemView.setOnClickListener {
                shouldRecreateOnReturn = true
                val activeRole = prefs.getString("app_device_role", "auto") ?: "auto"
                val targetActivityClass = if (activeRole == "slave") CompanionRemoteActivity::class.java else WebViewActivity::class.java

                startActivity(Intent(this, targetActivityClass).apply { putExtra("PRINTER_IP", printer.optString("ip", "")); putExtra("PRINTER_PORT", printer.optString("port", "7125")); if (printer.optString("defaultView", "") == "camera") putExtra("IS_CAMERA_VIEW", true) })
            }

            itemView.setOnLongClickListener {
                showPillDialog(printer.optString("name", "Drucker"), arrayOf(getString(R.string.choose_default_view_title), getString(R.string.yes_delete)), arrayOf(null, "#E53935")) { whichAction ->
                    if (whichAction == 0) {
                        showPillDialog(getString(R.string.choose_default_view_title), arrayOf(getString(R.string.menu_change_camera_type), getString(R.string.choose_default_view))) { whichView ->
                            try {
                                val arr = JSONArray(prefs.getString("printers_list", "[]"))
                                arr.getJSONObject(i).put("defaultView", if (whichView == 0) "camera" else "interface")
                                prefs.edit().putString("printers_list", arr.toString()).apply()
                                applyLanguageAndRefreshUI(); showCenteredPillToast(getString(R.string.choose_default_view_title) + " ✓")
                            } catch (e: Exception) { Log.e("KlippShell", "Fehler beim Ändern der Standardansicht", e) }
                        }
                    } else {
                        showPillDialog(getString(R.string.reset_confirm_msg), arrayOf(getString(R.string.yes_delete), getString(R.string.cancel)), arrayOf("#E53935", null)) { confirmDelete ->
                            if (confirmDelete == 0) {
                                val currentArray = JSONArray(prefs.getString("printers_list", "[]"))
                                val newList = JSONArray()
                                for (j in 0 until currentArray.length()) { if (j != i) newList.put(currentArray.get(j)) }
                                prefs.edit().putString("printers_list", newList.toString()).apply(); applyLanguageAndRefreshUI()
                            }
                        }
                    }
                }
                true
            }
            containerPrinters.addView(itemView)
        }
    }

    private fun applyLanguageAndRefreshUI() {
        val isFormVisible = containerAddPrinterForm.visibility == View.VISIBLE
        tvAddPrinterTitle.text = getString(if (isFormVisible) R.string.add_printer_up else R.string.add_printer_down)
        tvNoPrinter.text = getString(R.string.no_printers)
        findViewById<Button>(R.id.btnSearchNetwork)?.text = getString(R.string.btn_search_network)
        findViewById<Button>(R.id.btnAddMainPrinter)?.text = getString(R.string.btn_add)
        findViewById<Button>(R.id.btnExitApp)?.text = getString(R.string.btn_exit)

        etMainPrinterName.hint = getString(R.string.printer_name_hint)
        etMainPrinterIP.hint = getString(R.string.printer_ip_hint)
        etMainPrinterPort.hint = getString(R.string.printer_port_hint)
        actvMainPrinterModel.hint = getString(R.string.printer_model_hint)

        when (selectedSystemIndex) {
            0 -> btnSystemSelect.text = "Port: 4408"
            1 -> btnSystemSelect.text = "Port: 7125"
            2 -> btnSystemSelect.text = getString(R.string.system_manual)
        }
        loadPrinters()
    }

    private fun showCenteredPillToast(message: String) {
        val rootLayout = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pillView = TextView(this).apply {
            text = message; textSize = 16f; gravity = Gravity.CENTER; setTextColor(if (isNightMode) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 100f
                setColor(if (isNightMode) "#252B2E".toColorInt() else Color.WHITE)
                setStroke(4, if (isNightMode) Color.WHITE else "#BDBDBD".toColorInt())
            }
            setPadding(50, 35, 50, 35)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setMargins(50, 0, 50, 240)
            }
        }
        val container = FrameLayout(this).apply { addView(pillView) }
        rootLayout.addView(container)
        mainHandler.postDelayed({ if (!isFinishing && !isDestroyed) rootLayout.removeView(container) }, 2200)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in java.util.Collections.list(interfaces)) {
                for (addr in java.util.Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) return addr.hostAddress
                }
            }
        } catch (ex: Exception) { ex.printStackTrace() }
        return null
    }

    private fun searchNetworkForPrinters() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.search_network)
        val progressBar = ProgressBar(this).apply { setPadding(0, 24, 0, 24); indeterminateTintList = ColorStateList.valueOf("#2196F3".toColorInt()) }
        dialogView.findViewById<LinearLayout>(R.id.buttonContainer)?.addView(progressBar)

        val progressDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        progressDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progressDialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val ipPrefix = getLocalIpAddress()?.substringBeforeLast(".") ?: return@launch
            val foundPrinters = java.util.Collections.synchronizedList(mutableListOf<String>())
            val semaphore = Semaphore(20)
            val portsToCheck = arrayOf(4408, 7125)

            val jobs = (1..254).flatMap { i ->
                portsToCheck.map { port ->
                    launch {
                        semaphore.withPermit {
                            var socket: Socket? = null
                            try {
                                socket = Socket(); socket.connect(InetSocketAddress("$ipPrefix.$i", port), 350)
                                foundPrinters.add("$ipPrefix.$i:$port")
                            } catch (_: Exception) {} finally { try { socket?.close() } catch (_: Exception) {} }
                        }
                    }
                }
            }
            jobs.joinAll()

            withContext(Dispatchers.Main) {
                if (!this@MainActivity.isFinishing && !this@MainActivity.isDestroyed) {
                    try { progressDialog.dismiss() } catch (_: Exception) {}
                    val cleanList = foundPrinters.distinct()
                    if (cleanList.isNotEmpty()) {
                        val displayArray = cleanList.map { item ->
                            val parts = item.split(":")
                            val ip = parts[0]
                            val port = parts.getOrNull(1) ?: "7125"
                            val systemName = if (port == "4408") "Creality OS" else "Standard Klipper"
                            "$ip ($systemName)"
                        }.toTypedArray()

                        showPillDialog(getString(R.string.found_printers), displayArray, null) { which ->
                            val parts = cleanList[which].split(":")
                            etMainPrinterIP.setText(parts[0])
                            selectedSystemIndex = if (parts.getOrNull(1) == "4408") 0 else 1
                            btnSystemSelect.text = if (selectedSystemIndex == 0) "Port: 4408" else "Port: 7125"
                            etMainPrinterPort.visibility = View.GONE
                        }
                    } else { showCenteredPillToast(getString(R.string.toast_no_connection)) }
                }
            }
        }
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
            setPadding(24, 16, 24, 16); textSize = 15f; setTextColor(textColor); setHintTextColor(if (isNightMode) Color.GRAY else Color.parseColor("#757575"))
            isFocusable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
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
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded); backgroundTintList = ColorStateList.valueOf(cardBgColor); setPadding(16, 20, 16, 20)
                    layoutParams = GridLayout.LayoutParams().apply { width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT; columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f); setMargins(8, 8, 8, 8) }
                    onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                        v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(120).start()
                        (v.background as? GradientDrawable)?.setStroke(6, if (hasFocus) (if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else (if (isNightMode) Color.parseColor("#4DFFFFFF") else Color.parseColor("#33000000")))
                    }
                    setOnClickListener { onSelected(modelName); dialog.dismiss() }
                }
                kachel.addView(ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(toPx(64), toPx(64)).apply { setMargins(0, 0, 0, 8) }; scaleType = ImageView.ScaleType.CENTER_INSIDE; setImageResource(getPrinterImageResource(modelName)) })
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

    private fun savePrinter(name: String, ip: String, port: String, model: String, defaultView: String) {
        val list = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }
        try {
            list.put(JSONObject().put("name", name).put("ip", ip).put("port", port).put("model", model).put("defaultView", defaultView))
            prefs.edit().putString("printers_list", list.toString()).apply()
            applyLanguageAndRefreshUI()
            if (list.length() == 1) startTvBackgroundWorker()
        } catch (e: JSONException) { Log.e("KlippShell", "Fehler beim Speichern des Druckers", e) }
    }

    private fun checkUpdatesSilentlyInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://api.github.com/repos/Ship-of-Agony/KlippShell4Creality").openConnection() as HttpURLConnection).apply { requestMethod = "GET"; connectTimeout = 4000; readTimeout = 4000; useCaches = false; setRequestProperty("User-Agent", "KlippShell-App"); setRequestProperty("Accept", "application/vnd.github.v3+json") }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val arr = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                    if (arr.length() > 0) {
                        val latestVersionTag = arr.getJSONObject(0).optString("tag_name", "").replace("v", "").trim()
                        val assetsArray = arr.getJSONObject(0).optJSONArray("assets")
                        val downloadUrl = if (assetsArray != null && assetsArray.length() > 0) assetsArray.getJSONObject(0).optString("browser_download_url", "") else ""
                        val currentVersionName = try { packageManager.getPackageInfo(packageName, 0).versionName?.replace("v", "")?.trim() ?: "0.8.5" } catch (e: Exception) { "0.8.5" }
                        if ((latestVersionTag.replace(".", "").toIntOrNull() ?: 0) > (currentVersionName.replace(".", "").toIntOrNull() ?: 0) && downloadUrl.isNotEmpty()) {
                            withContext(Dispatchers.Main) { showUpdateAvailableDialog(latestVersionTag, downloadUrl) }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("KlippShell", "Lautloser Update-Check failed", e) } finally { connection?.disconnect() }
        }
    }

    private fun showUpdateAvailableDialog(newVersion: String, downloadUrl: String) {
        val options = arrayOf(getString(R.string.btn_download_now), getString(R.string.btn_later))
        showPillDialog(title = getString(R.string.update_available_title, newVersion), items = options, hexColors = arrayOf("#4CAF50", null)) { index ->
            if (index == 0 && downloadUrl.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    startActivity(intent)
                } catch (e: Exception) { showCenteredPillToast(getString(R.string.toast_update_browser_error)) }
            }
        }
    }

    private fun showPillDialog(title: String, items: Array<String>, hexColors: Array<String?>? = null, onSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNightMode) Color.WHITE else Color.BLACK
        val btnBgColor = if (isNightMode) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")

        items.forEachIndexed { index, itemText ->
            val btn = MaterialButton(this).apply {
                text = itemText; isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, 35, 0, 35)
                val customHex = hexColors?.getOrNull(index)
                if (customHex != null) { backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex)); setTextColor(Color.WHITE) }
                else { backgroundTintList = ColorStateList.valueOf(btnBgColor); setTextColor(textColor) }
                isFocusable = true; layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }
                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8; v.strokeColor = ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    } else { v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start(); (v as MaterialButton).strokeWidth = 0 }
                }
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }
            container?.addView(btn)
        }
        dialog.show()
    }

    private fun showPermissionRationaleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create().apply { setCancelable(false); setCanceledOnTouchOutside(false); setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK } }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.perm_dialog_title)
        val mainContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        mainContainer?.addView(TextView(this).apply { text = getString(R.string.perm_dialog_msg); textSize = 15f; setTextColor(if (isNightMode) Color.WHITE else Color.BLACK); setPadding(24, 16, 24, 32); gravity = Gravity.START }, 0)

        val btnAccept = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn); isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, 35, 0, 35); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50")); setTextColor(Color.WHITE); isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1.0f).scaleY(if (hasFocus) 1.04f else 1.0f).translationZ(if (hasFocus) 6f else 0f).setDuration(100).start()
                if (v is MaterialButton) { v.strokeWidth = if (hasFocus) 8 else 0; v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null }
            }
            setOnClickListener {
                prefs.edit().putBoolean("has_shown_permissions", true).apply(); dialog.dismiss()
                if (try { JSONArray(prefs.getString("printers_list", "[]")).length() } catch (_: Exception) { 0 } == 0) actvMainPrinterModel.requestFocus() else findViewById<View>(R.id.btnSettings)?.requestFocus()
                if (try { JSONArray(prefs.getString("printers_list", "[]")).length() } catch (_: Exception) { 0 } > 0) startTvBackgroundWorker()
            }
        }

        val btnDecline = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn_decline); isAllCaps = false; textSize = 16f; cornerRadius = 100; setPadding(0, 35, 0, 35); backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935")); setTextColor(Color.WHITE); isFocusable = true
            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1.0f).scaleY(if (hasFocus) 1.04f else 1.0f).translationZ(if (hasFocus) 6f else 0f).setDuration(100).start()
                if (v is MaterialButton) { v.strokeWidth = if (hasFocus) 8 else 0; v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNightMode) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null }
            }
            setOnClickListener { dialog.dismiss(); finishAffinity() }
        }

        mainContainer?.addView(btnAccept); mainContainer?.addView(btnDecline)
        dialog.show(); btnAccept.requestFocus()
    }

    override fun onDestroy() { mainHandler.removeCallbacksAndMessages(null); currentFocus?.clearFocus(); super.onDestroy() }

    private fun toPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}