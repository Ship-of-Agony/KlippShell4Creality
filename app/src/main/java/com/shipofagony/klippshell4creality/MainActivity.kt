package com.shipofagony.klippshell4creality

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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

    // RECREATE-SCHALTER: Verhindert Endlosschleifen beim Neu-Instanziieren des Layouts
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
        val savedLang = prefs.getString("app_lang", "de") ?: "de"
        val locale = Locale(savedLang)
        Locale.setDefault(locale)

        val config = newBase.resources.configuration
        config.setLocale(locale)

        val localizedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(localizedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedTheme)

        if (savedInstanceState != null) {
            shouldRecreateOnReturn = savedInstanceState.getBoolean("recreate_flag", false)
        }

        super.onCreate(savedInstanceState)

        intent?.data?.let { uri ->
            if (uri.scheme == "klippshell" && uri.host == "open.printer") {
                val printerArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (_: Exception) { JSONArray() }
                if (printerArray.length() > 0) {
                    val primaryPrinter = printerArray.getJSONObject(0)
                    val webViewIntent = Intent(this, WebViewActivity::class.java).apply {
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

        val printerArray = try {
            JSONArray(prefs.getString("printers_list", "[]"))
        } catch (e: Exception) {
            Log.e("KlippShell", "Initiale Druckerliste korrupt", e)
            JSONArray()
        }

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
                        if (printerArray.length() == 0) {
                            actvMainPrinterModel.requestFocus()
                        } else {
                            findViewById<View>(R.id.btnSettings)?.requestFocus()
                        }
                    }
                    .start()
            }
        }

        val ivBackgroundWatermark = findViewById<ImageView>(R.id.ivBackgroundWatermark)
        ivBackgroundWatermark?.alpha = if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) 0.15f else 0.08f

        btnSystemSelect.setOnClickListener {
            val systemOptions = arrayOf(
                getString(R.string.system_creality),
                getString(R.string.system_standard),
                getString(R.string.system_manual)
            )

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
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
                openModelGridMenu()
                true
            } else {
                false
            }
        }

        actvMainPrinterModel.setText("", false)

        findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        headerAddPrinter.setOnClickListener {
            val isVisible = containerAddPrinterForm.visibility == View.VISIBLE
            containerAddPrinterForm.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvAddPrinterTitle.text = getString(if (isVisible) R.string.add_printer_down else R.string.add_printer_up)
            if (!isVisible) actvMainPrinterModel.requestFocus()
        }

        findViewById<Button>(R.id.btnSearchNetwork)?.setOnClickListener { searchNetworkForPrinters() }
        findViewById<Button>(R.id.btnExitApp)?.setOnClickListener { finishAffinity() }

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
            findViewById(R.id.btnSettings),
            findViewById(R.id.btnSearchNetwork),
            findViewById<Button>(R.id.btnAddMainPrinter),
            findViewById(R.id.btnExitApp),
            headerAddPrinter,
            btnSystemSelect,
            etMainPrinterName,
            etMainPrinterIP,
            etMainPrinterPort,
            actvMainPrinterModel
        )

        buttons.forEach { btn ->
            btn?.isFocusable = true
            btn?.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.03f else 1.0f).scaleY(if (hasFocus) 1.03f else 1.0f).setDuration(150).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }
        }

        applyLanguageAndRefreshUI()

        val isAutoCheckEnabled = prefs.getBoolean("update_auto_check", true)
        if (isAutoCheckEnabled) {
            checkUpdatesSilentlyInBackground()
        }

        if (printerArray.length() > 0 && isAndroidTV()) {
            startTvBackgroundWorker()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("recreate_flag", shouldRecreateOnReturn)
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
                "KlipperTvKachelWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                tvWorkRequest
            )
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Starten des TV-Workers via Jetpack WorkManager", e)
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
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val pillView = TextView(this).apply {
            text = message
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(if (isNight) Color.WHITE else Color.BLACK)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(if (isNight) "#252B2E".toColorInt() else Color.WHITE)
                setStroke(4, if (isNight) Color.WHITE else "#BDBDBD".toColorInt())
            }
            setPadding(50, 35, 50, 35)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(50, 0, 50, 240)
            }
        }
        val container = FrameLayout(this).apply { addView(pillView) }
        rootLayout.addView(container)

        mainHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                rootLayout.removeView(container)
            }
        }, 2200)
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in java.util.Collections.list(interfaces)) {
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) return addr.hostAddress
                }
            }
        } catch (ex: Exception) { ex.printStackTrace() }
        return null
    }

    private fun searchNetworkForPrinters() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.search_network)

        val progressBar = ProgressBar(this).apply {
            setPadding(0, 24, 0, 24)
            indeterminateTintList = ColorStateList.valueOf("#2196F3".toColorInt())
        }
        dialogView.findViewById<LinearLayout>(R.id.buttonContainer)?.addView(progressBar)

        val progressDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

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
                                socket = Socket()
                                socket.connect(InetSocketAddress("$ipPrefix.$i", port), 350)
                                foundPrinters.add("$ipPrefix.$i:$port")
                            } catch (_: Exception) {
                            } finally {
                                try { socket?.close() } catch (_: Exception) {}
                            }
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
                            val selectedItem = cleanList[which]
                            val parts = selectedItem.split(":")
                            val detectedIp = parts[0]
                            val detectedPort = parts.getOrNull(1) ?: "7125"

                            etMainPrinterIP.setText(detectedIp)

                            if (detectedPort == "4408") {
                                selectedSystemIndex = 0
                                btnSystemSelect.text = "Port: 4408"
                                etMainPrinterPort.visibility = View.GONE
                            } else {
                                selectedSystemIndex = 1
                                btnSystemSelect.text = "Port: 7125"
                                etMainPrinterPort.visibility = View.GONE
                            }
                        }
                    } else {
                        showCenteredPillToast(getString(R.string.toast_no_connection))
                    }
                }
            }
        }
    }

    private fun showModelSelectionSearchDialog(
        title: String,
        allModels: Array<String>,
        onSelected: (String) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val mainContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val cardBgColor = ContextCompat.getColor(this, R.color.pill_normal_inactive)
        val textColor = ContextCompat.getColor(this, R.color.pill_normal_inactive_text)

        val etSearch = EditText(this).apply {
            hint = getString(R.string.search_model_hint)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_pill_input)
            setPadding(24, 16, 24, 16)
            textSize = 15f
            setTextColor(textColor)
            setHintTextColor(if (isNight) Color.GRAY else Color.parseColor("#757575"))
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        mainContainer.addView(etSearch)

        val scrollLayout = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toPx(320)).apply {
                clipToPadding = false
                clipChildren = false
            }
            isVerticalScrollBarEnabled = true
        }

        val gridLayout = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            isRowOrderPreserved = false
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                clipToPadding = false
                clipChildren = false
            }
        }
        scrollLayout.addView(gridLayout)
        mainContainer.addView(scrollLayout)

        fun populateList(filterText: String) {
            gridLayout.removeAllViews()
            val filteredList = allModels.filter { it.lowercase(Locale.getDefault()).contains(filterText.lowercase(Locale.getDefault())) }

            filteredList.forEach { modelName ->
                val kachel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isClickable = true
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_rounded)
                    backgroundTintList = ColorStateList.valueOf(cardBgColor)
                    setPadding(16, 20, 16, 20)

                    val p = GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(8, 8, 8, 8)
                    }
                    layoutParams = p

                    onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                        v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).setDuration(120).start()
                        val drawable = v.background as? GradientDrawable
                        if (drawable != null) {
                            if (hasFocus) {
                                drawable.setStroke(6, if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                            } else {
                                drawable.setStroke(2, if (isNight) Color.parseColor("#4DFFFFFF") else Color.parseColor("#33000000"))
                            }
                        }
                    }

                    setOnClickListener {
                        onSelected(modelName)
                        dialog.dismiss()
                    }
                }

                val ivPreview = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(toPx(64), toPx(64)).apply {
                        setMargins(0, 0, 0, 8)
                    }
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(getPrinterImageResource(modelName))
                }

                val tvName = TextView(this).apply {
                    text = modelName
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 2
                }

                kachel.addView(ivPreview)
                kachel.addView(tvName)
                gridLayout.addView(kachel)
            }
        }

        populateList("")

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populateList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog.show()
        etSearch.requestFocus()
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun savePrinter(name: String, ip: String, port: String, model: String, defaultView: String) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val list = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }

        try {
            list.put(JSONObject().put("name", name).put("ip", ip).put("port", port).put("model", model).put("defaultView", defaultView))
            prefs.edit().putString("printers_list", list.toString()).apply()
            applyLanguageAndRefreshUI()

            if (list.length() == 1 && isAndroidTV()) {
                startTvBackgroundWorker()
            }
        } catch (e: JSONException) {
            Log.e("KlippShell", "Fehler beim Speichern des Druckers", e)
        }
    }

    private fun loadPrinters() {
        containerPrinters.removeAllViews()
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        val list = try {
            JSONArray(prefs.getString("printers_list", "[]"))
        } catch (e: Exception) {
            Log.e("KlippShell", "JSON in SharedPreferences korrupt. Resette Liste.", e)
            prefs.edit { putString("printers_list", "[]") }
            JSONArray()
        }

        tvNoPrinter.visibility = if (list.length() == 0) View.VISIBLE else View.GONE

        // BIDIREKTIONALE LAYOUT WEICHE: Evaluiert die 3-Wege-Erzwingungs-Pille auch für das Hauptfenster!
        val isDualScreenMode = when (prefs.getInt("layout_mode_override", 0)) {
            1 -> false
            2 -> true
            else -> findViewById<View>(R.id.guidelineCenter) != null
        }

        if (list.length() == 0) {
            containerAddPrinterForm.isVisible = true
            tvAddPrinterTitle.text = getString(R.string.add_printer_up)
            if (isAndroidTV()) {
                WorkManager.getInstance(applicationContext).cancelUniqueWork("KlipperTvKachelWorker")
            }
        }

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isSinglePrinter = list.length() == 1

        for (i in 0 until list.length()) {
            val printer = try { list.getJSONObject(i) } catch (_: Exception) { null } ?: continue
            val itemView = LayoutInflater.from(this).inflate(R.layout.printer_item, containerPrinters, false)

            itemView.isFocusable = true
            itemView.isFocusableInTouchMode = false

            if (isSinglePrinter) {
                if (itemView is LinearLayout) {
                    itemView.orientation = LinearLayout.VERTICAL
                    itemView.gravity = Gravity.CENTER
                    itemView.setPadding(toPx(32), toPx(48), toPx(32), toPx(48))
                }
            }

            val tvName = itemView.findViewById<TextView>(R.id.tvPrinterNameAndAddress)

            itemView.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                val drawable = v.background as? GradientDrawable
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(8f).setDuration(150).start()
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
                    drawable?.setStroke(8, if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    if (isNight) tvName?.setTextColor(Color.WHITE)
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start()
                    v.backgroundTintList = null
                    drawable?.setStroke(2, if (isNight) Color.parseColor("#4DFFFFFF") else Color.parseColor("#33000000"))
                    if (isNight) tvName?.setTextColor(Color.parseColor("#FFFFFF")) else tvName?.setTextColor(Color.BLACK)
                }
            }

            val iconView = itemView.findViewById<ImageView>(R.id.ivPrinterIcon)
            if (iconView != null) {
                iconView.setImageResource(getPrinterImageResource(printer.optString("model", "")))

                if (isSinglePrinter) {
                    val sizePx = toPx(120)
                    iconView.layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                        setMargins(0, 0, 0, toPx(24))
                    }
                }
            }

            if (tvName != null) {
                tvName.text = printer.optString("name", "Unbekannt")
                tvName.textSize = if (isSinglePrinter) 24f else 18f
                tvName.setTypeface(null, android.graphics.Typeface.BOLD)

                if (isNight) {
                    tvName.setTextColor(Color.WHITE)
                } else {
                    tvName.setTextColor(Color.BLACK)
                }

                if (isSinglePrinter) {
                    tvName.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_HORIZONTAL
                    }
                }
            }

            itemView.setOnClickListener {
                shouldRecreateOnReturn = true

                val intent = Intent(this, WebViewActivity::class.java).apply {
                    putExtra("PRINTER_IP", printer.optString("ip", ""))
                    putExtra("PRINTER_PORT", printer.optString("port", "7125"))
                    if (printer.optString("defaultView", "") == "camera") {
                        putExtra("IS_CAMERA_VIEW", true)
                    }
                }
                startActivity(intent)
            }

            itemView.setOnLongClickListener {
                val actionOptions = arrayOf(getString(R.string.choose_default_view_title), getString(R.string.yes_delete))
                val actionColors = arrayOf<String?>(null, "#E53935")

                showPillDialog(printer.optString("name", "Drucker"), actionOptions, actionColors) { whichAction ->
                    if (whichAction == 0) {
                        val viewOptions = arrayOf(getString(R.string.menu_change_camera_type), getString(R.string.choose_default_view))
                        showPillDialog(getString(R.string.choose_default_view_title), viewOptions) { whichView ->
                            val newView = if (whichView == 0) "camera" else "interface"
                            val currentArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }
                            if (currentArray.length() > i) {
                                try {
                                    currentArray.getJSONObject(i).put("defaultView", newView)
                                    prefs.edit().putString("printers_list", currentArray.toString()).apply()
                                    applyLanguageAndRefreshUI()
                                    showCenteredPillToast(getString(R.string.choose_default_view_title) + " ✓")
                                } catch (e: Exception) { Log.e("KlippShell", "Fehler beim Ändern der Standardansicht", e) }
                            }
                        }
                    } else {
                        val deleteOptions = arrayOf(getString(R.string.yes_delete), getString(R.string.cancel))
                        val deleteColors = arrayOf<String?>("#E53935", null)

                        showPillDialog(getString(R.string.reset_confirm_msg), deleteOptions, deleteColors) { confirmDelete ->
                            if (confirmDelete == 0) {
                                val currentArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }
                                val newList = JSONArray()
                                for (j in 0 until currentArray.length()) { if (j != i) newList.put(currentArray.get(j)) }
                                prefs.edit().putString("printers_list", newList.toString()).apply()
                                applyLanguageAndRefreshUI()
                            }
                        }
                    }
                }
                true
            }
            containerPrinters.addView(itemView)
        }
    }

    private fun checkUpdatesSilentlyInBackground() {
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

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
                        } catch (e: Exception) { "0.8.5" }

                        val latestNumeric = latestVersionTag.replace(".", "").toIntOrNull() ?: 0
                        val currentVersionNameNumeric = currentVersionName.replace(".", "").toIntOrNull() ?: 0

                        if (latestNumeric > currentVersionNameNumeric && downloadUrl.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                showUpdateAvailableDialog(latestVersionTag, downloadUrl)
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.e("KlippShell", "Lautloser Update-Check fehlgeschlagen", e) } finally { connection?.disconnect() }
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

    private fun showPillDialog(
        title: String,
        items: Array<String>,
        hexColors: Array<String?>? = null,
        onSelected: (Int) -> Unit
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK
        val btnBgColor = if (isNight) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")

        items.forEachIndexed { index, itemText ->
            val customHex = hexColors?.getOrNull(index)

            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false
                textSize = 16f
                cornerRadius = 100
                setPadding(0, 35, 0, 35)

                if (customHex != null) {
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor(customHex))
                    setTextColor(Color.WHITE)
                } else {
                    backgroundTintList = ColorStateList.valueOf(btnBgColor)
                    setTextColor(textColor)
                }

                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 10, 0, 10) }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 8
                        v.strokeColor = ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1"))
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(100).start()
                        (v as MaterialButton).strokeWidth = 0
                    }
                }
                setOnClickListener { onSelected(index); dialog.dismiss() }
            }
            container?.addView(btn)
        }
        dialog.show()
    }

    private fun showPermissionRationaleDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, _ ->
            keyCode == android.view.KeyEvent.KEYCODE_BACK
        }

        dialogView.findViewById<TextView>(R.id.tvDialogTitle)?.text = getString(R.string.perm_dialog_title)
        val mainContainer = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK

        val msgView = TextView(this).apply {
            text = getString(R.string.perm_dialog_msg)
            textSize = 15f
            setTextColor(textColor)
            setPadding(24, 16, 24, 32)
            gravity = Gravity.START
        }
        mainContainer?.addView(msgView, 0)

        val btnAccept = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn)
            isAllCaps = false
            textSize = 16f
            cornerRadius = 100
            setPadding(0, 35, 0, 35)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            setTextColor(Color.WHITE)
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }

            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1.0f).scaleY(if (hasFocus) 1.04f else 1.0f).translationZ(if (hasFocus) 6f else 0f).setDuration(100).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }

            setOnClickListener {
                getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).edit().putBoolean("has_shown_permissions", true).apply()
                dialog.dismiss()

                val currentArray = try { JSONArray(getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).getString("printers_list", "[]")) } catch (_: Exception) { JSONArray() }
                if (currentArray.length() == 0) { actvMainPrinterModel.requestFocus() } else { findViewById<View>(R.id.btnSettings)?.requestFocus() }
                if (currentArray.length() > 0 && isAndroidTV()) { startTvBackgroundWorker() }
            }
        }

        val btnDecline = MaterialButton(this).apply {
            text = getString(R.string.perm_dialog_btn_decline)
            isAllCaps = false
            textSize = 16f
            cornerRadius = 100
            setPadding(0, 35, 0, 35)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E53935"))
            setTextColor(Color.WHITE)
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.04f else 1.0f).scaleY(if (hasFocus) 1.04f else 1.0f).translationZ(if (hasFocus) 6f else 0f).setDuration(100).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 8 else 0
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(if (isNight) Color.parseColor("#FFD54F") else Color.parseColor("#0288D1")) else null
                }
            }
            setOnClickListener { dialog.dismiss(); finishAffinity() }
        }

        mainContainer?.addView(btnAccept)
        mainContainer?.addView(btnDecline)

        dialog.show()
        btnAccept.requestFocus()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        currentFocus?.clearFocus()
        super.onDestroy()
    }

    private fun toPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}