package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class CompanionServerManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val onCommandReceived: (String) -> Unit,
    private val getCurrentHostIp: () -> String,
    private val getCurrentActiveUrl: () -> String
) {

    private var remoteServerJob: Job? = null
    private var remoteServerSocket: ServerSocket? = null

    private var udpDiscoveryJob: Job? = null
    private var udpDiscoverySocket: DatagramSocket? = null

    fun startServers() {
        val role = prefs.getString("app_device_role", "auto") ?: "auto"
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        if (role == "slave") return
        if (role == "auto" && !isTv) return

        startRemoteServerSocket()
        startUdpDiscoveryServer()
    }

    fun stopServers() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
        try { udpDiscoverySocket?.close() } catch (_: Exception) {}
        udpDiscoverySocket = null

        remoteServerJob?.cancel()
        remoteServerJob = null
        try { remoteServerSocket?.close() } catch (_: Exception) {}
        remoteServerSocket = null
    }

    private fun startRemoteServerSocket() {
        remoteServerJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(9999))
                remoteServerSocket = server

                while (isActive) {
                    val socket = remoteServerSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        try {
                            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                            val command = reader.readLine()?.trim()

                            if (!command.isNullOrEmpty()) {
                                if (command == "REQUEST_PRINTER_INFO") {
                                    val currentIp = getCurrentHostIp()
                                    val currentUrl = getCurrentActiveUrl()
                                    val currentPort = android.net.Uri.parse(currentUrl).port.takeIf { it != -1 }?.toString() ?: "7125"
                                    val printersJson = prefs.getString("printers_list", "[]") ?: "[]"

                                    var modelName = "Standard Drucker"
                                    var printerName = "KlippShell TV"

                                    try {
                                        val arr = JSONArray(printersJson)
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.getJSONObject(i)
                                            if (obj.optString("ip") == currentIp) {
                                                modelName = obj.optString("model", "Standard Drucker")
                                                printerName = obj.optString("name", "KlippShell TV")
                                                break
                                            }
                                        }
                                    } catch (_: Exception) {}

                                    val jsonResponse = JSONObject().apply {
                                        put("ip", currentIp)
                                        put("port", currentPort)
                                        put("model", modelName)
                                        put("name", printerName)
                                    }

                                    writer.write(jsonResponse.toString())
                                    writer.newLine()
                                    writer.flush()
                                } else {
                                    withContext(Dispatchers.Main) {
                                        onCommandReceived(command)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("KlippShell", "Companion remote command error", e)
                        } finally {
                            try { socket.close() } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "Companion Server Socket failed", e)
            }
        }
    }

    private fun startUdpDiscoveryServer() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            try {
                udpDiscoverySocket = DatagramSocket(9998).apply { reuseAddress = true }
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpDiscoverySocket?.receive(packet)

                    val requestMsg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (requestMsg == "DISCOVER_KLIPPSHELL_MASTER") {
                        val currentIp = getCurrentHostIp().takeIf { it.isNotEmpty() } ?: "127.0.0.1"
                        val currentUrl = getCurrentActiveUrl()
                        val currentPort = android.net.Uri.parse(currentUrl).port.takeIf { it != -1 }?.toString() ?: "7125"
                        val printersJson = prefs.getString("printers_list", "[]") ?: "[]"

                        var modelName = "Standard Drucker"
                        var printerName = "KlippShell TV"

                        try {
                            val arr = JSONArray(printersJson)
                            if (arr.length() > 0) {
                                val obj = arr.getJSONObject(0)
                                modelName = obj.optString("model", "Standard Drucker")
                                printerName = obj.optString("name", "KlippShell TV")
                            }
                        } catch (_: Exception) {}

                        val responseJson = JSONObject().apply {
                            put("ip", currentIp)
                            put("port", currentPort)
                            put("model", modelName)
                            put("name", printerName)
                        }

                        val responseData = responseJson.toString().toByteArray(Charsets.UTF_8)
                        val responsePacket = DatagramPacket(
                            responseData,
                            responseData.size,
                            packet.address,
                            packet.port
                        )
                        udpDiscoverySocket?.send(responsePacket)
                    }
                }
            } catch (e: Exception) {
                Log.e("KlippShell", "UDP Auto Discovery Server Error", e)
            }
        }
    }
}