package com.shipofagony.klippshell4creality

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket

class CompanionServerManager(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val onCommandReceived: (String) -> Unit,
    private val getCurrentPrinterIp: () -> String,
    private val getCurrentActiveUrl: () -> String,
    private val getLiveState: () -> JSONObject = { JSONObject() }
) {

    private var remoteServerJob: Job? = null
    private var remoteServerSocket: ServerSocket? = null

    private var udpDiscoveryJob: Job? = null
    private var udpDiscoverySocket: DatagramSocket? = null

    private var nsdManager: NsdManager? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    fun startServers() {
        val role = prefs.getString("app_device_role", "auto") ?: "auto"
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        if (role == "slave" || role == "disabled") return
        if (role == "auto" && !isTv) return

        stopServers()
        startRemoteServerSocket()
        startUdpDiscoveryServer()
        registerNsdService()
    }

    fun stopServers() {
        unregisterNsdService()

        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
        try { udpDiscoverySocket?.close() } catch (_: Exception) {}
        udpDiscoverySocket = null

        remoteServerJob?.cancel()
        remoteServerJob = null
        try { remoteServerSocket?.close() } catch (_: Exception) {}
        remoteServerSocket = null
    }

    private fun getLocalDeviceIpv4(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.isNotEmpty() && !ip.startsWith("127.")) return ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("KlippShell", "Fehler beim Ermitteln der lokalen TV-IP", e)
        }
        return ""
    }

    private fun buildStatusPayload(fallbackTvIp: String): JSONObject {
        val tvIp = getLocalDeviceIpv4().ifEmpty { fallbackTvIp }
        val activePrinterIp = getCurrentPrinterIp()
        val currentUrl = getCurrentActiveUrl()
        val currentPort = android.net.Uri.parse(currentUrl).port.takeIf { it != -1 }?.toString() ?: "7125"
        val printersJson = prefs.getString("printers_list", "[]") ?: "[]"

        var modelName = context.getString(R.string.printer_model_default)
        var printerName = context.getString(R.string.app_name)

        try {
            val arr = JSONArray(printersJson)
            var found = false
            if (activePrinterIp.isNotEmpty()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optString("ip") == activePrinterIp) {
                        modelName = obj.optString("model", modelName)
                        printerName = obj.optString("name", printerName)
                        found = true
                        break
                    }
                }
            }
            if (!found && arr.length() > 0) {
                val first = arr.getJSONObject(0)
                modelName = first.optString("model", modelName)
                printerName = first.optString("name", printerName)
            }
        } catch (_: Exception) {}

        val live = try { getLiveState() } catch (_: Exception) { JSONObject() }

        return JSONObject().apply {
            put("ip", tvIp)
            put("port", currentPort)
            put("model", modelName)
            put("name", printerName)
            if (live.has("isVideoMode")) put("isVideoMode", live.optBoolean("isVideoMode", false))
            if (live.has("isOsdOn")) put("isOsdOn", live.optBoolean("isOsdOn", false))
            if (live.has("isPipMode")) put("isPipMode", live.optBoolean("isPipMode", false))
            if (live.has("osdStyle")) put("osdStyle", live.optString("osdStyle", "box"))
        }
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
                            socket.tcpNoDelay = true
                            socket.keepAlive = true
                            socket.soTimeout = 15000
                            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)

                            while (isActive && !socket.isClosed) {
                                val line = reader.readLine() ?: break
                                val command = line.trim()
                                if (command.isEmpty()) continue

                                when (command) {
                                    "PING" -> {
                                        writer.write("PONG\n")
                                        writer.flush()
                                    }
                                    "REQUEST_PRINTER_INFO", "REQUEST_SYNC_STATE" -> {
                                        val fallbackIp = socket.localAddress?.hostAddress ?: ""
                                        val jsonResponse = buildStatusPayload(fallbackIp)
                                        writer.write(jsonResponse.toString())
                                        writer.newLine()
                                        writer.flush()
                                    }
                                    else -> {
                                        withContext(Dispatchers.Main) {
                                            onCommandReceived(command)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("KlippShell", "Companion client disconnected: ${e.message}")
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
        udpDiscoveryJob = scope.launch(Dispatchers.IO) {
            try {
                udpDiscoverySocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(9998))
                }
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpDiscoverySocket?.receive(packet)

                    val requestMsg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                    if (requestMsg == "DISCOVER_KLIPPSHELL_MASTER") {
                        val fallbackIp = udpDiscoverySocket?.localAddress?.hostAddress ?: ""
                        val responseJson = buildStatusPayload(fallbackIp)
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
            } finally {
                try { udpDiscoverySocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun registerNsdService() {
        try {
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = manager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "KlippShellTV"
                serviceType = "_klippshell._tcp."
                port = 9999
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    Log.d("KlippShell", "NSD Service registriert: ${info.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w("KlippShell", "NSD Registrierung fehlgeschlagen: $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo) {}
                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            }
            nsdRegistrationListener = listener
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e("KlippShell", "NSD Startfehler", e)
        }
    }

    private fun unregisterNsdService() {
        try {
            nsdRegistrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (_: Exception) {}
        nsdRegistrationListener = null
    }
}