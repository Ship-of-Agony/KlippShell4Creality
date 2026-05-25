package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
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

    // Status-Speicher: 0 = Creality (Standard), 1 = Klipper, 2 = Manuell
    private var selectedSystemIndex = 0

    private class CenteredDialogAdapter(context: Context, items: Array<String>) :
        ArrayAdapter<String>(context, android.R.layout.select_dialog_item, android.R.id.text1, items) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent) as TextView
            view.gravity = Gravity.CENTER
            val isNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            view.setTextColor(if (isNight) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            view.setPadding(0, 30, 0, 30)
            return view
        }
    }

    private fun createCenteredTitle(titleText: String): TextView {
        return TextView(this).apply {
            text = titleText
            setPadding(0, 40, 0, 10)
            textSize = 20f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            setTextColor(if (isNight) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        }
    }

    private fun getPrinterImageResource(modelName: String): Int {
        val formattedName = modelName.lowercase(Locale.getDefault())
            .replace(" ", "").replace("-", "_")
        val resourceName = "printer_$formattedName"
        val resId = resources.getIdentifier(resourceName, "drawable", packageName)
        return if (resId != 0) resId else R.mipmap.ic_launcher
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(if (prefs.getBoolean("dark_mode", false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        setContentView(R.layout.activity_main)

        val startupVeil = findViewById<LinearLayout?>(R.id.viewStartupVeil)
        startupVeil?.post {
            startupVeil.animate()
                .translationY(-(startupVeil.height.toFloat()))
                .alpha(0f)
                .setStartDelay(800)
                .setDuration(1200)
                .withEndAction {
                    startupVeil.visibility = View.GONE
                }
                .start()
        }

        val ivBackgroundWatermark = findViewById<ImageView>(R.id.ivBackgroundWatermark)
        ivBackgroundWatermark?.alpha = if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) 0.15f else 0.08f

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

        btnSystemSelect.setOnClickListener {
            val systemOptions = arrayOf(
                getString(R.string.system_creality),
                getString(R.string.system_standard),
                getString(R.string.system_manual)
            )

            val dialog = AlertDialog.Builder(this@MainActivity)
                .setCustomTitle(createCenteredTitle("System wählen"))
                .setAdapter(CenteredDialogAdapter(this@MainActivity, systemOptions)) { _, which ->
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
                .create()
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
            dialog.show()
        }

        val printerModels = arrayOf("CR-10", "Ender 3 V3 KE", "K1", "K1 Max", "K2 Plus", "Sonic Pad (Ender 3 S1)", "Sonic Pad (Ender 5 S1)", "Sermoon D3", "GS-01")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, printerModels)
        actvMainPrinterModel.setAdapter(adapter)

        val buttons = arrayOf(
            findViewById<View>(R.id.btnSettings),
            findViewById<View>(R.id.btnSearchNetwork),
            findViewById<Button>(R.id.btnAddMainPrinter),
            findViewById<Button>(R.id.btnExitApp),
            headerAddPrinter,
            btnSystemSelect
        )

        buttons.forEach { btn ->
            btn.isFocusable = true
            btn.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.05f else 1.0f).scaleY(if (hasFocus) 1.05f else 1.0f).start()
                if (v is MaterialButton) {
                    v.strokeWidth = if (hasFocus) 6 else 0
                    v.strokeColor = if (hasFocus) android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE) else null
                }
            }
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            try {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Fehler beim Öffnen der Einstellungen!", Toast.LENGTH_SHORT).show()
            }
        }

        headerAddPrinter.setOnClickListener {
            val isVisible = containerAddPrinterForm.visibility == View.VISIBLE
            containerAddPrinterForm.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvAddPrinterTitle.text = getString(if (isVisible) R.string.add_printer_down else R.string.add_printer_up)
        }

        findViewById<Button>(R.id.btnSearchNetwork).setOnClickListener {
            searchNetworkForPrinters()
        }

        findViewById<Button>(R.id.btnExitApp).setOnClickListener {
            finishAffinity()
        }

        findViewById<Button>(R.id.btnAddMainPrinter).setOnClickListener {
            val name = etMainPrinterName.text.toString().trim()
            val ip = etMainPrinterIP.text.toString().trim()
            val port = when (selectedSystemIndex) {
                0 -> "4408"
                1 -> "80"
                else -> etMainPrinterPort.text.toString().trim().ifEmpty { "80" }
            }

            if (name.isNotEmpty() && ip.isNotEmpty()) {
                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setCustomTitle(createCenteredTitle(getString(R.string.choose_default_view)))
                    .setAdapter(CenteredDialogAdapter(this@MainActivity, arrayOf("Interface", "Kamera"))) { _, which ->
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

                        Toast.makeText(this@MainActivity, "Drucker hinzugefügt", Toast.LENGTH_SHORT).show()
                    }
                    .create()
                dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                dialog.show()
            } else {
                val errorDialog = AlertDialog.Builder(this@MainActivity)
                    .setCustomTitle(createCenteredTitle("Fehlende Eingabe"))
                    .setMessage("Bitte gib mindestens einen Namen und eine IP-Adresse ein.")
                    .setPositiveButton("OK", null)
                    .create()
                errorDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                errorDialog.show()
            }
        }

        // Hier wird die Druckerliste beim Start geladen!
        loadPrinters()
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in java.util.Collections.list(interfaces)) {
                val addrs = java.util.Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
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
        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#2196F3"))

        val progressDialog = AlertDialog.Builder(this@MainActivity)
            .setCustomTitle(createCenteredTitle(getString(R.string.search_network)))
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
            executor.awaitTermination(6, java.util.concurrent.TimeUnit.SECONDS)
            runOnUiThread {
                progressDialog.dismiss()
                if (foundPrinters.isNotEmpty()) {
                    val uniquePrinters = foundPrinters.distinct().toTypedArray()
                    val centeredAdapter = CenteredDialogAdapter(this@MainActivity, uniquePrinters)

                    val foundDialog = AlertDialog.Builder(this@MainActivity)
                        .setCustomTitle(createCenteredTitle(getString(R.string.found_printers)))
                        .setAdapter(centeredAdapter) { _, which ->
                            etMainPrinterIP.setText(uniquePrinters[which])
                            selectedSystemIndex = 0
                            btnSystemSelect.text = "Creality\n(4408)"
                            etMainPrinterPort.visibility = View.GONE
                        }
                        .setPositiveButton(getString(R.string.cancel), null)
                        .create()

                    foundDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                    foundDialog.show()
                } else {
                    Toast.makeText(this@MainActivity, "Keine Drucker gefunden.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun savePrinter(name: String, ip: String, port: String, model: String, defaultView: String) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = JSONArray(prefs.getString("printers_list", "[]"))
        printerArray.put(JSONObject().put("name", name).put("ip", ip).put("port", port).put("model", model).put("defaultView", defaultView))
        prefs.edit().putString("printers_list", printerArray.toString()).apply()
        loadPrinters()
    }

    private fun loadPrinters() {
        containerPrinters.removeAllViews()
        val printerArray = JSONArray(getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE).getString("printers_list", "[]"))

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
                    v.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#44FFFFFF"))
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationZ(0f).setDuration(150).start()
                    v.backgroundTintList = null
                }
            }

            val iconView = itemView.findViewById<ImageView>(R.id.ivPrinterIcon)
            if (iconView != null) {
                iconView.setImageResource(getPrinterImageResource(printer.getString("model")))
            }

            itemView.findViewById<TextView>(R.id.tvPrinterNameAndAddress).apply {
                text = printer.getString("name")
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, WebViewActivity::class.java)
                val targetUrl = if (printer.getString("defaultView") == "camera") "http://${printer.getString("ip")}/camera.html" else "http://${printer.getString("ip")}:${printer.getString("port")}"
                intent.putExtra("TARGET_URL", targetUrl)
                startActivity(intent)
            }

            itemView.setOnLongClickListener {
                val mainAdapter = CenteredDialogAdapter(this@MainActivity, arrayOf(getString(R.string.choose_default_view), "Drucker löschen"))
                val actionDialog = AlertDialog.Builder(this@MainActivity)
                    .setCustomTitle(createCenteredTitle(printer.getString("name")))
                    .setAdapter(mainAdapter) { _, whichAction ->
                        if (whichAction == 0) {
                            val viewAdapter = CenteredDialogAdapter(this@MainActivity, arrayOf("Interface", "Kamera"))
                            val changeDialog = AlertDialog.Builder(this@MainActivity)
                                .setCustomTitle(createCenteredTitle(getString(R.string.choose_default_view)))
                                .setAdapter(viewAdapter) { _, whichView ->
                                    val newView = if (whichView == 0) "interface" else "camera"
                                    val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                    val currentArray = JSONArray(prefs.getString("printers_list", "[]"))
                                    currentArray.getJSONObject(i).put("defaultView", newView)
                                    prefs.edit().putString("printers_list", currentArray.toString()).apply()
                                    loadPrinters()
                                    Toast.makeText(this@MainActivity, "Ansicht geändert", Toast.LENGTH_SHORT).show()
                                }
                                .create()
                            changeDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                            changeDialog.show()
                        } else {
                            val deleteDialog = AlertDialog.Builder(this@MainActivity)
                                .setCustomTitle(createCenteredTitle("Drucker löschen?"))
                                .setMessage(getString(R.string.reset_confirm_msg))
                                .setPositiveButton(getString(R.string.yes_delete)) { _, _ ->
                                    val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
                                    val currentArray = JSONArray(prefs.getString("printers_list", "[]"))
                                    val newList = JSONArray()
                                    for (j in 0 until currentArray.length()) { if (j != i) newList.put(currentArray.get(j)) }
                                    prefs.edit().putString("printers_list", newList.toString()).apply()
                                    loadPrinters()
                                }
                                .setNegativeButton(getString(R.string.cancel), null)
                                .create()
                            deleteDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                            deleteDialog.show()
                        }
                    }
                    .create()
                actionDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                actionDialog.show()
                true
            }
            containerPrinters.addView(itemView)
        }
    }
}