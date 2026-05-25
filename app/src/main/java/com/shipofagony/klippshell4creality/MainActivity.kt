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
        if (modelName == "Sonic Pad (Ender 3 S1)") return resources.getIdentifier("printer_sonic_ender_3s1", "drawable", packageName)
        if (modelName == "Sonic Pad (Ender 5 S1)") return resources.getIdentifier("printer_sonic_ender_5s1", "drawable", packageName)

        val formattedName = modelName.lowercase(Locale.getDefault())
            .replace(" ", "")
            .replace("-", "_")

        val resourceName = "printer_$formattedName"
        val resId = resources.getIdentifier(resourceName, "drawable", packageName)

        return if (resId != 0) resId else R.mipmap.ic_launcher
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(if (prefs.getBoolean("dark_mode", false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)

        setContentView(R.layout.activity_main)

        // --- NEU: Dark Mode Wasserzeichen Fix ---
        val ivBackgroundWatermark = findViewById<ImageView>(R.id.ivBackgroundWatermark)
        if (ivBackgroundWatermark != null) {
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                ivBackgroundWatermark.alpha = 0.15f // Im Dark Mode kräftiger
            } else {
                ivBackgroundWatermark.alpha = 0.08f // Im Light Mode dezent
            }
        }

        containerPrinters = findViewById(R.id.containerPrinters)
        tvNoPrinter = findViewById(R.id.tvNoPrinter)
        etMainPrinterName = findViewById(R.id.etMainPrinterName)
        etMainPrinterIP = findViewById(R.id.etMainPrinterIP)
        etMainPrinterPort = findViewById(R.id.etMainPrinterPort)
        actvMainPrinterModel = findViewById(R.id.actvMainPrinterModel)
        headerAddPrinter = findViewById(R.id.headerAddPrinter)
        containerAddPrinterForm = findViewById(R.id.containerAddPrinterForm)
        tvAddPrinterTitle = findViewById(R.id.tvAddPrinterTitle)

        val printerModels = arrayOf(
            "CR-10", "CR-10 SE", "CR-10 Smart", "CR-10 Smart Pro", "CR-10S Pro V2",
            "CR-20 Pro", "CR-30", "CR-6 SE", "CR-M4", "CR-M4 SE",
            "Ender 2 Pro", "Ender 3", "Ender 3 Max", "Ender 3 Max Neo", "Ender 3 Neo",
            "Ender 3 S1", "Ender 3 S1 Plus", "Ender 3 S1 Pro", "Ender 3 V2", "Ender 3 V3",
            "Ender 3 V3 KE", "Ender 3 V3 Plus", "Ender 3 V3 SE", "Ender 3 V4",
            "Ender 5 Max", "Ender 5 Plus", "Ender 5 S1",
            "GS-01", "GS-02", "GS-03", "GS-04", "HI",
            "K1", "K1C", "K1 Max", "K1 SE", "K2", "K2 Plus", "K2 Pro", "K2 SE",
            "Sermoon D3", "Sermoon D3 Pro", "Sermoon M300", "Sermoon V1 Pro",
            "Sonic Pad (Ender 3 S1)", "Sonic Pad (Ender 5 S1)", "Spark Xi7"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, printerModels)
        actvMainPrinterModel.setAdapter(adapter)

        actvMainPrinterModel.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) actvMainPrinterModel.showDropDown()
        }
        actvMainPrinterModel.setOnClickListener {
            actvMainPrinterModel.showDropDown()
        }

        val btnSettings = findViewById<View>(R.id.btnSettings)
        val btnSearchNetwork = findViewById<View>(R.id.btnSearchNetwork)
        val btnAddMainPrinter = findViewById<Button>(R.id.btnAddMainPrinter)
        val btnExitApp = findViewById<Button>(R.id.btnExitApp)

        // --- Starker TV-Fokus ---
        val tvFocusListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.animate().scaleX(1.05f).scaleY(1.05f).alpha(1.0f).translationZ(8f).setDuration(150).start()
                if (view is com.google.android.material.button.MaterialButton) {
                    view.strokeWidth = 6
                    view.strokeColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                } else {
                    view.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#44FFFFFF"))
                }
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).alpha(0.8f).translationZ(0f).setDuration(150).start()
                if (view is com.google.android.material.button.MaterialButton) {
                    view.strokeWidth = 0
                } else {
                    view.backgroundTintList = null
                }
            }
        }

        arrayOf(btnSettings, btnSearchNetwork, btnAddMainPrinter, btnExitApp, headerAddPrinter).forEach { btn ->
            btn.isFocusable = true
            btn.alpha = 0.8f
            btn.onFocusChangeListener = tvFocusListener
        }

        headerAddPrinter.setOnClickListener {
            val isVisible = containerAddPrinterForm.visibility == View.VISIBLE
            containerAddPrinterForm.visibility = if (isVisible) View.GONE else View.VISIBLE
            tvAddPrinterTitle.text = if (isVisible) {
                this@MainActivity.getString(R.string.add_printer_down)
            } else {
                this@MainActivity.getString(R.string.add_printer_up)
            }
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnSearchNetwork.setOnClickListener {
            searchNetworkForPrinters()
        }

        btnAddMainPrinter.setOnClickListener {
            val name = etMainPrinterName.text.toString().trim()
            val ip = etMainPrinterIP.text.toString().trim()
            var port = etMainPrinterPort.text.toString().trim()
            if (port.isEmpty()) port = "4408"

            if (name.isNotEmpty() && ip.isNotEmpty()) {
                val options = arrayOf("Interface", "Kamera")
                val centeredAdapter = CenteredDialogAdapter(this, options)

                val viewDialog = AlertDialog.Builder(this)
                    .setCustomTitle(createCenteredTitle(getString(R.string.choose_default_view)))
                    .setAdapter(centeredAdapter) { _, which ->
                        val defaultView = if (which == 0) "interface" else "camera"
                        savePrinter(name, ip, port, actvMainPrinterModel.text.toString().trim(), defaultView)

                        etMainPrinterName.text.clear()
                        etMainPrinterIP.text.clear()
                        etMainPrinterPort.setText("4408")
                        actvMainPrinterModel.text.clear()

                        containerAddPrinterForm.visibility = View.GONE
                        tvAddPrinterTitle.text = this@MainActivity.getString(R.string.add_printer_down)

                        Toast.makeText(this, "Drucker hinzugefügt", Toast.LENGTH_SHORT).show()
                    }
                    .setCancelable(false)
                    .create()

                viewDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                viewDialog.show()

            } else {
                val dialog = AlertDialog.Builder(this)
                    .setCustomTitle(createCenteredTitle("Fehlende Eingabe"))
                    .setMessage("Bitte gib mindestens einen Namen und eine IP-Adresse ein.")
                    .setPositiveButton("OK", null)
                    .create()
                dialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                dialog.show()
            }
        }

        btnExitApp.setOnClickListener {
            finishAffinity()
        }

        loadPrinters()
    }

    override fun onResume() {
        super.onResume()
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

        val progressDialog = AlertDialog.Builder(this)
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
                    val centeredAdapter = CenteredDialogAdapter(this, uniquePrinters)

                    val foundDialog = AlertDialog.Builder(this)
                        .setCustomTitle(createCenteredTitle(getString(R.string.found_printers)))
                        .setAdapter(centeredAdapter) { _, which ->
                            etMainPrinterIP.setText(uniquePrinters[which])
                            etMainPrinterPort.setText("4408")
                        }
                        .setPositiveButton(getString(R.string.cancel), null)
                        .create()

                    foundDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                    foundDialog.show()
                } else {
                    Toast.makeText(this, "Keine Drucker gefunden.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun savePrinter(name: String, ip: String, port: String, model: String, defaultView: String) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = JSONArray(prefs.getString("printers_list", "[]"))
        val newPrinter = JSONObject().put("name", name).put("ip", ip).put("port", port).put("model", model).put("defaultView", defaultView)
        printerArray.put(newPrinter)
        prefs.edit().putString("printers_list", printerArray.toString()).apply()
        loadPrinters()
    }

    private fun updatePrinterView(index: Int, newView: String) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = JSONArray(prefs.getString("printers_list", "[]"))
        if (index < printerArray.length()) {
            val printer = printerArray.getJSONObject(index)
            printer.put("defaultView", newView)
            prefs.edit().putString("printers_list", printerArray.toString()).apply()
            loadPrinters()
        }
    }

    private fun loadPrinters() {
        containerPrinters.removeAllViews()
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = JSONArray(prefs.getString("printers_list", "[]") ?: "[]")

        tvNoPrinter.text = getString(R.string.no_printers)
        tvNoPrinter.visibility = if (printerArray.length() == 0) View.VISIBLE else View.GONE

        if (printerArray.length() == 0) {
            containerAddPrinterForm.visibility = View.VISIBLE
            tvAddPrinterTitle.text = getString(R.string.add_printer_up)
        }

        for (i in 0 until printerArray.length()) {
            val printer = printerArray.getJSONObject(i)
            val name = printer.getString("name")
            val ip = printer.getString("ip")
            val port = printer.getString("port")
            val model = printer.getString("model")
            val defaultView = printer.optString("defaultView", "interface")

            val itemView: View = LayoutInflater.from(this).inflate(R.layout.printer_item, containerPrinters, false)

            itemView.isFocusable = true

            // --- Leucht-Fokus für Druckerliste ---
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
                iconView.setImageResource(getPrinterImageResource(model))
            }

            itemView.findViewById<TextView>(R.id.tvPrinterNameAndAddress).apply {
                text = name
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, WebViewActivity::class.java)
                val targetUrl = if (defaultView == "camera") "http://$ip/camera.html" else "http://$ip:$port"
                intent.putExtra("TARGET_URL", targetUrl)
                startActivity(intent)
            }

            itemView.setOnLongClickListener {
                val mainOptions = arrayOf(getString(R.string.choose_default_view), "Drucker löschen")
                val mainAdapter = CenteredDialogAdapter(this@MainActivity, mainOptions)

                val actionDialog = AlertDialog.Builder(this@MainActivity)
                    .setCustomTitle(createCenteredTitle(name))
                    .setAdapter(mainAdapter) { _, whichAction ->
                        if (whichAction == 0) {
                            val viewOptions = arrayOf("Interface", "Kamera")
                            val viewAdapter = CenteredDialogAdapter(this@MainActivity, viewOptions)

                            val changeDialog = AlertDialog.Builder(this@MainActivity)
                                .setCustomTitle(createCenteredTitle(getString(R.string.choose_default_view)))
                                .setAdapter(viewAdapter) { _, whichView ->
                                    val newView = if (whichView == 0) "interface" else "camera"
                                    updatePrinterView(i, newView)
                                    Toast.makeText(this@MainActivity, "Ansicht geändert", Toast.LENGTH_SHORT).show()
                                }
                                .create()
                            changeDialog.window?.setBackgroundDrawableResource(R.drawable.bg_card)
                            changeDialog.show()
                        } else {
                            val deleteDialog = AlertDialog.Builder(this@MainActivity)
                                .setCustomTitle(createCenteredTitle("Drucker löschen?"))
                                .setMessage(getString(R.string.reset_confirm_msg))
                                .setPositiveButton(getString(R.string.yes_delete)) { _, _ -> deletePrinter(i) }
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

    private fun deletePrinter(index: Int) {
        val prefs = getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printerArray = JSONArray(prefs.getString("printers_list", "[]"))
        val newList = JSONArray()
        for (i in 0 until printerArray.length()) { if (i != index) newList.put(printerArray.get(i)) }
        prefs.edit().putString("printers_list", newList.toString()).apply()
        loadPrinters()
    }
}