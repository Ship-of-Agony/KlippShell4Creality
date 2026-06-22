package com.shipofagony.klippshell4creality

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MoonrakerPollManager(
    private val getHostIp: () -> String,
    private val getCurrentActiveUrl: () -> String,
    private val onDataFetched: (String) -> Unit
) {
    private var pollingJob: Job? = null
    private var cachedMoonrakerUrl: URL? = null
    
    var knownChamberSensor: String? = "temperature_sensor chamber_temp"
    var knownChamberHeater: String? = "heater_generic chamber_heater"
    private var cachedChamberQueryString: String = "&temperature_sensor%20chamber_temp&heater_generic%20chamber_heater"

    val chamberSensorsToTry = listOf("temperature_sensor chamber_temp", "temperature_sensor chamber", "temperature_sensor enclosure_temp", "temperature_sensor enclosure")
    val chamberHeatersToTry = listOf("heater_generic chamber_heater", "heater_generic chamber", "heater_generic enclosure_heater", "enclosure_heater")

    fun updateMoonrakerUrl() {
        val host = getHostIp()
        if (host.isEmpty()) {
            cachedMoonrakerUrl = null
            return
        }
        try {
            val qs = "&output_pin%20fan0&output_pin%20fan2&temperature_fan%20chamber_fan&output_pin%20LED$cachedChamberQueryString"
            cachedMoonrakerUrl = URL("http://$host:7125/printer/objects/query?extruder&heater_bed&print_stats&display_status$qs")
        } catch (e: Exception) { 
            cachedMoonrakerUrl = null 
        }
    }

    fun runChamberAutoSearch(status: JSONObject) {
        for (sensor in chamberSensorsToTry) { if (status.has(sensor)) { knownChamberSensor = sensor; break } }
        for (heater in chamberHeatersToTry) { if (status.has(heater)) { knownChamberHeater = heater; break } }
        cachedChamberQueryString = "&${Uri.encode(knownChamberSensor)}&${Uri.encode(knownChamberHeater)}"
        updateMoonrakerUrl()
    }

    fun startPolling(scope: CoroutineScope, isOffline: () -> Boolean) {
        updateMoonrakerUrl()
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val responseText = fetchMoonrakerData()
                withContext(Dispatchers.Main) {
                    onDataFetched(responseText)
                }
                val currentDelay = if (isOffline()) 2000L else 3000L
                delay(currentDelay)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun fetchMoonrakerData(): String {
        val targetUrl = cachedMoonrakerUrl ?: return ""
        var conn: HttpURLConnection? = null
        return try {
            conn = targetUrl.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
        } catch (_: Exception) {
            ""
        } finally {
            conn?.disconnect()
        }
    }
}