package app.poolt.network

import app.poolt.core.Device
import app.poolt.core.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

object SsdpDiscovery {
    suspend fun discover(timeoutMs: Int = 2200): List<Device> = withContext(Dispatchers.IO) {
        val request = (
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n\r\n"
        ).toByteArray()
        val found = linkedMapOf<String, Device>()

        DatagramSocket().use { socket ->
            socket.soTimeout = 350
            socket.broadcast = true
            socket.send(DatagramPacket(request, request.size, InetAddress.getByName("239.255.255.250"), 1900))
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                try {
                    val buffer = ByteArray(8192)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val text = String(packet.data, 0, packet.length)
                    val headers = parseHeaders(text)
                    val location = headers["location"]
                    val marker = (headers["server"] ?: "") + " " + (headers["usn"] ?: "") + " " + (headers["st"] ?: "")
                    val ip = runCatching { location?.let { URI(it).host } }.getOrNull()
                        ?: packet.address.hostAddress ?: continue
                    val lower = marker.lowercase()
                    val driverId = when {
                        lower.contains("roku") -> "roku.ecp"
                        lower.contains("samsung") -> "samsung.smarttv"
                        lower.contains("webos") || lower.contains("lge") || lower.contains("lg ") -> "lg.webos"
                        else -> null
                    } ?: continue
                    val brand = when (driverId) {
                        "roku.ecp" -> "Roku"
                        "samsung.smarttv" -> "Samsung"
                        "lg.webos" -> "LG"
                        else -> "TV"
                    }
                    found[driverId + "@" + ip] = Device(
                        id = driverId + "@" + ip,
                        name = brand + " · " + ip,
                        type = if (driverId == "roku.ecp") DeviceType.MEDIA_PLAYER else DeviceType.TV,
                        driverId = driverId,
                        brand = brand,
                        model = "Найдено в сети",
                        address = ip,
                        isOnline = true
                    )
                } catch (_: java.net.SocketTimeoutException) {
                }
            }
        }
        found.values.toList()
    }

    private fun parseHeaders(text: String): Map<String, String> =
        text.lineSequence().drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
}
