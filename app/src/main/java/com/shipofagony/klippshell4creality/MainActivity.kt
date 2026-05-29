package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    private val printerMap = mapOf(
        "CR-10" to "cr_10", "CR-10 SE" to "cr_10se", "CR-10 Smart" to "cr_10smart",
        "CR-10 Smart Pro" to "cr_10smartpro", "CR-10S Pro V2" to "cr_10sprov2",
        "CR-20 Pro" to "cr_20pro", "CR-30" to "cr_30", "CR-6 SE" to "cr_6se",
        "CR-M4" to "cr_m4", "CR-M4 SE" to "cr_m4se", "Ender 2 Pro" to "ender_2pro",
        "Ender 3" to "ender_3", "Ender 3 Max" to "ender_3max", "Ender 3 Max Neo" to "ender_3maxneo",
        "Ender 3 Neo" to "ender_3neo", "Ender 3 S1" to "ender_3s1", "Ender 3 S1 Plus" to "ender_3s1plus",
        "Ender 3 S1 Pro" to "ender_3s1pro", "Ender 3 V2" to "ender_3v2", "Ender 3 V3" to "ender_3v3",
        "Ender 3 V3 KE" to "ender_3v3ke", "Ender 3 V3 Plus" to "ender_3v3plus", "Ender 3 V3 SE" to "ender_3v3se",
        "Ender 4" to "ender_4", "Ender 5 Max" to "ender_5max", "Ender 5 Plus" to "ender_5plus",
        "Ender 5 S1" to "ender_5s1", "GS-01" to "gs_01", "GS-02" to "gs_02", "GS-03" to "gs_03",
        "GS-04" to "gs_04", "HI" to "hi", "K1" to "k1", "K1C" to "k1c", "K1 Max" to "k1max",
        "K1 SE" to "k1se", "K2" to "k2", "K2 Plus" to "k2plus", "K2 Pro" to "k2pro",
        "K2 SE" to "k2se", "Sermoon D3" to "sermoond3", "Sermoon D3 Pro" to "sermoond3pro",
        "Sermoon M300" to "sermoonm300", "Sermoon V1 Pro" to "sermoonv1pro",
        "Sonic Pad (Ender 3 S1)" to "sonic_ender_3s1", "Sonic Pad (Ender 5 S1)" to "sonic_ender_5s1",
        "Spark Xi7" to "sparkxi7"
    )

    private fun getPrinterImageResource(modelName: String): Int {
        val mappedName = printerMap[modelName] ?: modelName.lowercase(Locale.getDefault()).replace(" ", "").replace("-", "_")
        val resourceName = "printer_$mappedName"
        val resId = resources.getIdentifier(resourceName, "drawable", packageName)
        return if (resId != 0) resId else R.mipmap.ic_launcher
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        val savedTheme = try { prefs.getInt("theme_state", 0) } catch (e: Exception) { 0 }
        when (savedTheme) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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

        val printerArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }

        val startupVeil = findViewById<LinearLayout?>(R.id.viewStartupVeil)
        startupVeil?.post {
            startupVeil.animate()
                .translationY(-(startupVeil.height.toFloat()))
                .alpha(0f)
                .setStartDelay(500)
                .setDuration(800)
                .withEndAction {
                    startupVeil.visibility = View.GONE
                    if (printerArray.length() == 0) {
                        etMainPrinterName.requestFocus()
                    } else {
                        findViewById<View>(R.id.btnSettings)?.requestFocus()
                    }
                }
                .start()
        }

        val ivBackgroundWatermark = findViewById<ImageView>(R.id.ivBackgroundWatermark)
        ivBackgroundWatermark?.alpha = if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) 0.15f else 0.08f

        btnSystemSelect.setOnClickListener {
            val systemOptions = arrayOf(
                getString(R.string.system_creality),
                getString(R.string.system_standard),
                getString(R.string.system_manual)
            )

            showPillDialog("System wählen", systemOptions) { which ->
                selectedSystemIndex = which
                when (which) {
                    0 -> {
                        btnSystemSelect.text = "Creality\n(4408)"
                        etMainPrinterPort.visibility = View.GONE
                    }
                    1 -> {
                        btnSystemSelect.text = "Klipper\n(80)"
                        etMainPrinterPort.visibility = View.GONE
                    }
                    2 -> {
                        btnSystemSelect.text = "Manuell\n(Port)"
                        etMainPrinterPort.visibility = View.VISIBLE
                        etMainPrinterPort.requestFocus()
                    }
                }
            }
        }

        val printerModels = printerMap.keys.toTypedArray()
        val adapter = ArrayAdapter(this, R.layout.item_dropdown_pill, printerModels)
        actvMainPrinterModel.setAdapter(adapter)
        actvMainPrinterModel.inputType = InputType.TYPE_NULL

        actvMainPrinterModel.setOnClickListener { actvMainPrinterModel.showDropDown() }
        actvMainPrinterModel.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvMainPrinterModel.showDropDown() }

        val buttons = arrayOf(
            findViewById<View>(R.id.btnSettings),
            findViewById<View>(R.id.btnSearchNetwork),
            findViewById<Button>(R.id.btnAddMainPrinter),
            findViewById<Button>(R.id.btnExitApp),
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
                    v.strokeWidth = if (hasFocus) 6 else 0
                    v.strokeColor = if (hasFocus) ColorStateList.valueOf(Color.WHITE) else null
                }
            }
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }

        headerAddPrinter.setOnClickListener {
            val isVisible = containerAddPrinterForm.visibility == View.VISIBLE
            containerAddPrinterForm.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvAddPrinterTitle.text = getString(if (isVisible) R.string.add_printer_down else R.string.add_printer_up)
            if (!isVisible) {
                etMainPrinterName.requestFocus()
            }
        }

        findViewById<Button>(R.id.btnSearchNetwork).setOnClickListener { searchNetworkForPrinters() }
        findViewById<Button>(R.id.btnExitApp).setOnClickListener { finishAffinity() }

        findViewById<Button>(R.id.btnAddMainPrinter).setOnClickListener {
            val name = etMainPrinterName.text.toString().trim()
            val ip = etMainPrinterIP.text.toString().trim()
            val port = when (selectedSystemIndex) {
                0 -> "4408"
                1 -> "80"
                else -> etMainPrinterPort.text.toString().trim().ifEmpty { "80" }
            }

            if (name.isNotEmpty() && ip.isNotEmpty()) {
                showPillDialog(getString(R.string.choose_default_view), arrayOf("Interface", "Kamera")) { which ->
                    savePrinter(name, ip, port, actvMainPrinterModel.text.toString().trim(), if (which == 0) "interface" else "camera")

                    etMainPrinterName.text.clear()
                    etMainPrinterIP.text.clear()
                    etMainPrinterPort.text.clear()
                    actvMainPrinterModel.text.clear()

                    selectedSystemIndex = 0
                    btnSystemSelect.text = "Creality\n(4408)"
                    etMainPrinterPort.visibility = View.GONE
                    containerAddPrinterForm.visibility = View.GONE
                    tvAddPrinterTitle.text = getString(R.string.add_printer_down)

                    showCenteredPillToast("Drucker hinzugefügt")
                }
            } else {
                showPillDialog("Fehlende Eingabe", arrayOf("Verstanden")) { }
            }
        }

        loadPrinters()
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
        Handler(Looper.getMainLooper()).postDelayed({ rootLayout.removeView(container) }, 2200)
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
        val ipAddress = getLocalIpAddress()
        if (ipAddress == null) {
            Toast.makeText(this, "Kein Netzwerk gefunden", Toast.LENGTH_LONG).show()
            return
        }

        val ipPrefix = ipAddress.substring(0, ipAddress.lastIndexOf(".") + 1)
        val foundPrinters = mutableListOf<String>()

        val progressBar = ProgressBar(this)
        progressBar.setPadding(0, 50, 0, 50)
        progressBar.indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#2196F3"))

        val progressDialog = AlertDialog.Builder(this@MainActivity)
            .setCustomTitle(TextView(this).apply {
                text = getString(R.string.search_network)
                gravity = Gravity.CENTER
                textSize = 20f
                setPadding(0, 40, 0, 10)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            .setView(progressBar)
            .setCancelable(false)
            .create()

        progressDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
        progressDialog.show()

        val executor = Executors.newFixedThreadPool(50)
        for (i in 1..254) {
            val testIp = "$ipPrefix$i"
            executor.execute {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(testIp, 4408), 300)
                    socket.close()
                    foundPrinters.add(testIp)
                } catch (_: Exception) { }
            }
        }
        executor.shutdown()

        Thread {
            executor.awaitTermination(6, TimeUnit.SECONDS)
            runOnUiThread {
                progressDialog.dismiss()
                if (foundPrinters.isNotEmpty()) {
                    val uniquePrinters = foundPrinters.distinct().toTypedArray()
                    showPillDialog(getString(R.string.found_printers), uniquePrinters) { which ->
                        etMainPrinterIP.setText(uniquePrinters[which])
                        selectedSystemIndex = 0
                        btnSystemSelect.text = "Creality\n(4408)"
                        etMainPrinterPort.visibility = View.GONE
                    }
                } else {
                    showCenteredPillToast("Keine Drucker gefunden.")
                }
            }
        }.start()
    }

    private fun savePrinter(name: String, ip: String, port: String, model: String, defaultView: String) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }
        printerArray.put(JSONObject().put("name", name).put("ip", ip).put("port", port).put("model", model).put("defaultView", defaultView))
        prefs.edit().putString("printers_list", printerArray.toString()).apply()
        loadPrinters()
    }

    private fun loadPrinters() {
        containerPrinters.removeAllViews()
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }

        tvNoPrinter.visibility = if (printerArray.length() == 0) View.VISIBLE else View.GONE
        if (printerArray.length() == 0) {
            containerAddPrinterForm.visibility = View.VISIBLE
            tvAddPrinterTitle.text = getString(R.string.add_printer_up)
        }

        for (i in 0 until printerArray.length()) {
            val printer = printerArray.getJSONObject(i)
            val itemView = LayoutInflater.from(this).inflate(R.layout.printer_item, containerPrinters, false)

            itemView.isFocusable = true
            itemView.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).translationZ(8f).setDuration(150).start()
                    v.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start()
                    v.backgroundTintList = null
                }
            }

            val iconView = itemView.findViewById<ImageView>(R.id.ivPrinterIcon)
            if (iconView != null) iconView.setImageResource(getPrinterImageResource(printer.getString("model")))

            itemView.findViewById<TextView>(R.id.tvPrinterNameAndAddress).apply {
                text = printer.getString("name")
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, WebViewActivity::class.java).apply {
                    val targetUrl = if (printer.getString("defaultView") == "camera") {
                        "http://${printer.getString("ip")}/camera.html"
                    } else {
                        "http://${printer.getString("ip")}:${printer.getString("port")}"
                    }
                    putExtra("TARGET_URL", targetUrl)
                }
                startActivity(intent)
            }

            itemView.setOnLongClickListener {
                showPillDialog(printer.getString("name"), arrayOf(getString(R.string.choose_default_view), getString(R.string.yes_delete))) { whichAction ->
                    if (whichAction == 0) {
                        showPillDialog(getString(R.string.choose_default_view), arrayOf("Interface", "Kamera")) { whichView ->
                            val newView = if (whichView == 0) "interface" else "camera"
                            val currentArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }
                            if (currentArray.length() > i) {
                                currentArray.getJSONObject(i).put("defaultView", newView)
                                prefs.edit().putString("printers_list", currentArray.toString()).apply()
                                loadPrinters()
                                showCenteredPillToast("Ansicht geändert")
                            }
                        }
                    } else {
                        showPillDialog(getString(R.string.reset_confirm_msg), arrayOf(getString(R.string.yes_delete), getString(R.string.cancel))) { confirmDelete ->
                            if (confirmDelete == 0) {
                                val currentArray = try { JSONArray(prefs.getString("printers_list", "[]")) } catch (e: Exception) { JSONArray() }
                                val newList = JSONArray()
                                for (j in 0 until currentArray.length()) { if (j != i) newList.put(currentArray.get(j)) }
                                prefs.edit().putString("printers_list", newList.toString()).apply()
                                loadPrinters()
                            }
                        }
                    }
                }
                true
            }
            containerPrinters.addView(itemView)
        }
    }

    private fun showPillDialog(title: String, items: Array<String>, onSelected: (Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_view, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        val container = dialogView.findViewById<LinearLayout>(R.id.buttonContainer)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val textColor = if (isNight) Color.WHITE else Color.BLACK
        val btnBgColor = if (isNight) Color.parseColor("#33FFFFFF") else Color.parseColor("#1A888888")

        items.forEachIndexed { index, itemText ->
            val btn = MaterialButton(this).apply {
                text = itemText
                isAllCaps = false // FIXED: Korrekte Methode für Kotlin
                textSize = 16f    // FIXED: Float Literal statt XML "sp" Suffix
                cornerRadius = 100
                setPadding(0, 35, 0, 35)
                backgroundTintList = ColorStateList.valueOf(btnBgColor)
                setTextColor(textColor)
                isFocusable = true

                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 10, 0, 10)
                }

                onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) {
                        v.animate().scaleX(1.04f).scaleY(1.04f).translationZ(6f).setDuration(100).start()
                        strokeWidth = 6
                        strokeColor = ColorStateList.valueOf(Color.WHITE)
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