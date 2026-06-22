package com.shipofagony.klippshell4creality

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections

class NetworkScanHelper(private val scope: CoroutineScope) {

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in Collections.list(interfaces)) {
                for (addr in Collections.list(intf.inetAddresses)) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    fun scanNetworkForPrinters(onScanCompleted: (List<String>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val localIp = getLocalIpAddress()
            if (localIp == null) {
                withContext(Dispatchers.Main) { onScanCompleted(emptyList()) }
                return@launch
            }

            val ipPrefix = localIp.substringBeforeLast(".")
            val foundPrinters = Collections.synchronizedList(mutableListOf<String>())
            val semaphore = Semaphore(20)
            val portsToCheck = arrayOf(4408, 7125)

            val jobs = (1..254).flatMap { i ->
                portsToCheck.map { port ->
                    launch {
                        semaphore.withPermit {
                            var socket: Socket? = null
                            try {
                                socket = Socket()
                                socket.connect(InetSocketAddress("$ipPrefix.$i", port), 750)
                                foundPrinters.add("$ipPrefix.$i:$port")
                            } catch (_: Exception) {} finally {
                                try { socket?.close() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
            jobs.joinAll()

            val cleanList = foundPrinters.distinct()
            withContext(Dispatchers.Main) {
                onScanCompleted(cleanList)
            }
        }
    }
}