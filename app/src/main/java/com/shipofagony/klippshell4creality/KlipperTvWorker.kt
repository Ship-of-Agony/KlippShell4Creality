package com.shipofagony.klippshell4creality

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class KlipperTvWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        val printersJson = prefs.getString("printers_list", "[]") ?: "[]"

        val printerArray = try { JSONArray(printersJson) } catch (e: Exception) { JSONArray() }

        // Wenn keine Drucker konfiguriert sind, brechen wir sauber ab
        if (printerArray.length() == 0) {
            return@withContext Result.success()
        }

        // Hole den ersten (primären) Drucker aus der Liste
        val primaryPrinter = printerArray.getJSONObject(0)
        val printerName = primaryPrinter.optString("name", "Klipper")
        val printerIp = primaryPrinter.optString("ip", "")
        val printerPort = primaryPrinter.optString("port", "7125")

        if (printerIp.isEmpty()) {
            return@withContext Result.success()
        }

        // 1. Datenabfrage über die Moonraker-API im Hintergrund
        val responseText = fetchPrinterStatus(printerIp, printerPort)

        var titleText = "$printerName - ${context.getString(R.string.osd_printer_offline)}"
        var descText = context.getString(R.string.toast_no_connection)
        var isOnline = false

        if (responseText.isEmpty()) {
            // NEU: Offline-Erkennung scharfschalten (feuert nur, wenn er vorher online war)
            val wasOnline = prefs.getBoolean("last_known_online_state", false)
            if (wasOnline) {
                prefs.edit().putBoolean("last_known_online_state", false).apply()
                triggerNotification("offline", R.string.notify_title_offline, R.string.notify_msg_offline)
            }
        }

        if (responseText.isNotEmpty()) {
            try {
                val json = JSONObject(responseText)
                val status = json.optJSONObject("result")?.optJSONObject("status")
                if (status != null) {
                    isOnline = true
                    prefs.edit().putBoolean("last_known_online_state", true).apply()

                    val extruder = status.optJSONObject("extruder")
                    val tempExtruder = extruder?.optDouble("temperature", 0.0) ?: 0.0

                    val bed = status.optJSONObject("heater_bed")
                    val tempBed = bed?.optDouble("temperature", 0.0) ?: 0.0

                    val displayStatus = status.optJSONObject("display_status")
                    val progress = displayStatus?.optDouble("progress", 0.0) ?: 0.0

                    val printStats = status.optJSONObject("print_stats")
                    val currentState = printStats?.optString("state", "Standby") ?: "Standby"
                    val filename = printStats?.optString("filename", "") ?: "unknown_print"

                    val progressPercent = String.format(Locale.getDefault(), "%.1f%%", progress * 100)
                    titleText = "$printerName - $currentState ($progressPercent)"
                    descText = "Düse: ${String.format(Locale.getDefault(), "%.1f", tempExtruder)}°C | Bett: ${String.format(Locale.getDefault(), "%.1f", tempBed)}°C"

                    // NEU: Meilenstein-Auswertung während des Druckvorgangs
                    if (currentState == "printing") {
                        // Erste Schicht (Fortschritt liegt knapp über 0 aber unter 3%)
                        if (progress in 0.001..0.03) {
                            checkAndFireMilestone(filename, "first_layer", R.string.notify_title_first_layer, R.string.notify_msg_first_layer)
                        }
                        // 50% Meilenstein
                        if (progress >= 0.50 && progress < 0.75) {
                            checkAndFireMilestone(filename, "50", R.string.notify_title_50, R.string.notify_msg_50)
                        }
                        // 75% Meilenstein
                        if (progress >= 0.75 && progress < 0.90) {
                            checkAndFireMilestone(filename, "75", R.string.notify_title_75, R.string.notify_msg_75)
                        }
                        // 90% Meilenstein
                        if (progress >= 0.90 && progress < 0.99) {
                            checkAndFireMilestone(filename, "90", R.string.notify_title_90, R.string.notify_msg_90)
                        }
                    } else if (currentState == "complete" || currentState == "cancelled") {
                        if (currentState == "complete") {
                            checkAndFireMilestone(filename, "100", R.string.notify_title_100, R.string.notify_msg_100)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "KlipperTvWorker: Fehler beim Parsen der JSON-Daten", e)
            }
        }

        // 2. Kamera-Snapshot-URI bestimmen
        val posterUri = if (isOnline) {
            Uri.parse("http://$printerIp:$printerPort/webcam/?action=snapshot&t=${System.currentTimeMillis()}")
        } else {
            Uri.parse("android.resource://${context.packageName}/${R.mipmap.ic_launcher}")
        }

        // 3. Android TV Launcher Provider speisen
        try {
            val channelId = getOrCreateTvChannel(context)
            if (channelId != -1L) {
                updatePreviewProgram(context, channelId, titleText, descText, posterUri)
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "KlipperTvWorker: Fehler beim Schreiben in den TV-Provider", e)
        }

        Result.success()
    }

    // NEU: Hilfsmethode, um doppelte Meldungen pro Druckdatei zu verhindern
    private fun checkAndFireMilestone(filename: String, milestone: String, titleRes: Int, msgRes: Int) {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        // Bereinigt den Dateinamen von Sonderzeichen für einen sicheren XML-Key
        val safeFilename = filename.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val prefKey = "notified_${safeFilename}_$milestone"

        if (!prefs.getBoolean(prefKey, false)) {
            prefs.edit().putBoolean(prefKey, true).apply()
            triggerNotification(milestone, titleRes, msgRes)
        }
    }

    // NEU: Brücke zu den Sound- und Popup-Managern unter Berücksichtigung der User-Einstellungen
    private fun triggerNotification(key: String, titleRes: Int, msgRes: Int) {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)

        // 1. Sound abspielen, falls im Menü aktiviert
        if (prefs.getBoolean("sound_$key", true)) {
            try {
                SoundManager.playLiveNotification("sound_$key")
            } catch (e: Exception) {
                Log.e("KlippShell", "Worker: Sound-Preview fehlgeschlagen", e)
            }
        }

        // 2. Popup auf dem Bildschirm einblenden, falls im Menü aktiviert
        if (prefs.getBoolean("popup_$key", true)) {
            // Da der Worker im Hintergrund-Thread läuft, müssen wir das UI-Popup auf dem Haupt-Thread (MainLooper) ausführen
            Handler(Looper.getMainLooper()).post {
                try {
                    NotificationManager.showLivePopup(context, "popup_$key", titleRes, msgRes) {}
                } catch (e: Exception) {
                    Log.e("KlippShell", "Worker: Live-Popup-Overlay fehlgeschlagen", e)
                }
            }
        }
    }

    private fun fetchPrinterStatus(ip: String, port: String): String {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("http://$ip:$port/printer/objects/query?extruder&heater_bed&print_stats&display_status")
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.useCaches = false
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                return connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "KlipperTvWorker: Fehler bei API-Abfrage", e)
        } finally {
            connection?.disconnect()
        }
        return ""
    }

    private fun getOrCreateTvChannel(context: Context): Long {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        var channelId = prefs.getLong("tv_launcher_channel_id", -1L)

        if (channelId != -1L) {
            val cursor = context.contentResolver.query(
                TvContractCompat.buildChannelUri(channelId),
                arrayOf(TvContractCompat.Channels._ID),
                null, null, null
            )
            val exists = cursor?.use { it.moveToFirst() } ?: false
            if (exists) return channelId
        }

        val cursor = context.contentResolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(TvContractCompat.Channels._ID),
            "${TvContractCompat.Channels.COLUMN_DISPLAY_NAME} = ?",
            arrayOf("KlippShell"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val foundId = it.getLong(it.getColumnIndexOrThrow(TvContractCompat.Channels._ID))
                prefs.edit().putLong("tv_launcher_channel_id", foundId).apply()
                return foundId
            }
        }

        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName("KlippShell")
            .setDescription("3D Printer Monitor")
            .setAppLinkIntentUri(Uri.parse("klippshell://open.printer"))
            .build()

        val uri = context.contentResolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
        if (uri != null) {
            val newId = ContentUris.parseId(uri)
            prefs.edit().putLong("tv_launcher_channel_id", newId).apply()
            TvContractCompat.requestChannelBrowsable(context, newId)
            return newId
        }

        return -1L
    }

    private fun updatePreviewProgram(context: Context, channelId: Long, title: String, description: String, posterUri: Uri) {
        val prefs = context.getSharedPreferences("KlippShellPrefs", Context.MODE_PRIVATE)
        var programId = prefs.getLong("tv_launcher_program_id", -1L)

        if (programId != -1L) {
            val cursor = context.contentResolver.query(
                TvContractCompat.buildPreviewProgramUri(programId),
                arrayOf(TvContractCompat.PreviewPrograms._ID),
                null, null, null
            )
            val exists = cursor?.use { it.moveToFirst() } ?: false
            if (!exists) programId = -1L
        }

        val builder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
            .setTitle(title)
            .setDescription(description)
            .setIntentUri(Uri.parse("klippshell://open.printer"))
            .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            .setPosterArtUri(posterUri)

        if (programId == -1L) {
            val uri = context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                builder.build().toContentValues()
            )
            if (uri != null) {
                programId = ContentUris.parseId(uri)
                prefs.edit().putLong("tv_launcher_program_id", programId).apply()
            }
        } else {
            context.contentResolver.update(
                TvContractCompat.buildPreviewProgramUri(programId),
                builder.build().toContentValues(),
                null, null
            )
        }
    }
}