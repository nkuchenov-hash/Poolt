package app.poolt.network

import android.content.Context
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.poolt.core.Device
import app.poolt.core.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.net.SocketTimeoutException

/** Single Android SSDP scan with multicast lock and vendor-specific discovery. */
object SsdpDiscovery {
    suspend fun discover(context: Context? = null, timeoutMs: Int = 4700): List<Device> =
        withContext(Dispatchers.IO) {
            val wifi = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("Poolt-SSDP")?.apply { setReferenceCounted(false) }
            try {
                runCatching { lock?.acquire() }
                val targets = listOf(
                    "ssdp:all", "upnp:rootdevice",
                    "urn:lge-com:service:webos-second-screen:1",
                    "roku:ecp", "urn:samsung.com:device:RemoteControlReceiver:1"
                )
                val found = linkedMapOf<String, Device>()
                DatagramSocket().use { socket ->
                    // Route discovery over Wi-Fi even when Android's default route is a VPN.
                    val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val wifiNetwork = cm?.allNetworks?.firstOrNull { net ->
                        cm.getNetworkCapabilities(net)
                            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    }
                    runCatching { wifiNetwork?.bindSocket(socket) }
                    socket.soTimeout = 250
                    socket.broadcast = true
                    val multicast = InetAddress.getByName("239.255.255.250")
                    // Two spaced rounds: several LG webOS models answer only
                    // a later search packet after waking their SSDP service.
                    val wifiLan = context?.let { LanProbeDiscovery.wifiLan(it) }
                    val broadcastAddress = wifiLan?.ip?.address?.takeIf { wifiLan.prefix >= 24 }
                        ?.let { bytes ->
                            val octets = bytes.map { it.toInt() and 0xFF }
                            InetAddress.getByName("${octets[0]}.${octets[1]}.${octets[2]}.255")
                        }
                    val requests = targets.map { st ->
                        ("M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $st\r\n\r\n").toByteArray(Charsets.UTF_8)
                    }
                    fun sendQueries() {
                        requests.forEach { request ->
                            runCatching { socket.send(DatagramPacket(request, request.size, multicast, 1900)) }
                        }
                        // Some routers forward broadcast but suppress multicast.
                        broadcastAddress?.let { address ->
                            requests.firstOrNull()?.let { request ->
                                runCatching { socket.send(DatagramPacket(request, request.size, address, 1900)) }
                            }
                        }
                    }
                    sendQueries()
                    val deadline = System.currentTimeMillis() + timeoutMs
                    var retried = false
                    while (System.currentTimeMillis() < deadline) {
                        if (!retried && System.currentTimeMillis() >= deadline - timeoutMs / 2) {
                            retried = true
                            sendQueries()
                        }
                        try {
                            val data = ByteArray(8192)
                            val packet = DatagramPacket(data, data.size)
                            socket.receive(packet)
                            val headers = parseHeaders(String(data, 0, packet.length, Charsets.UTF_8))
                            val marker = listOf("server", "usn", "st", "location", "x-user-agent")
                                .joinToString(" ") { headers[it].orEmpty() }.lowercase()
                            val driverId = when {
                                marker.contains("roku") -> "roku.ecp"
                                marker.contains("webos") || marker.contains("lge") ||
                                    marker.contains("lgsmarttv") || marker.contains("lg smart tv") ||
                                    marker.contains("lg electronics") || marker.contains("netcast") -> "lg.webos"
                                marker.contains("samsung") || marker.contains("sec:main") -> "samsung.smarttv"
                                else -> null
                            } ?: continue
                            val ip = runCatching { headers["location"]?.let { URI(it).host } }.getOrNull()
                                ?: packet.address.hostAddress ?: continue
                            val brand = when(driverId) {
                                "lg.webos" -> "LG"
                                "roku.ecp" -> "Roku"
                                else -> "Samsung"
                            }
                            val device = Device(
                                id = "$driverId@$ip",
                                name = if (brand == "Roku") "Roku" else "$brand Smart TV",
                                type = if (brand == "Roku") DeviceType.MEDIA_PLAYER else DeviceType.TV,
                                driverId = driverId, brand = brand,
                                model = "Найден в локальной сети", address = ip,
                                isOnline = false
                            )
                            found[device.id] = device
                        } catch (_: SocketTimeoutException) { }
                    }
                }
                found.values.toList()
            } finally {
                runCatching { if (lock?.isHeld == true) lock.release() }
            }
        }

    private fun parseHeaders(response: String): Map<String, String> =
        response.lineSequence().drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null
            else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
}
