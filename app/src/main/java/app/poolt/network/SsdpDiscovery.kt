package app.poolt.network

import android.content.Context
import android.net.wifi.WifiManager
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
                    socket.soTimeout = 250
                    socket.broadcast = true
                    val multicast = InetAddress.getByName("239.255.255.250")
                    targets.forEach { st ->
                        val request = (
                            "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: 239.255.255.250:1900\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\n" +
                            "ST: $st\r\n\r\n"
                        ).toByteArray(Charsets.UTF_8)
                        runCatching { socket.send(DatagramPacket(request, request.size, multicast, 1900)) }
                    }
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            val data = ByteArray(8192)
                            val packet = DatagramPacket(data, data.size)
                            socket.receive(packet)
                            val headers = parseHeaders(String(data, 0, packet.length, Charsets.UTF_8))
                            val marker = listOf("server", "usn", "st", "location", "x-user-agent")
                                .joinToString(" ") { headers[it].orEmpty() }.lowercase()
                            val driverId = when {
                                marker.contains("roku") -> "roku.ecp"
                                marker.contains("webos") || marker.contains("lge") || marker.contains("lgsmarttv") -> "lg.webos"
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
