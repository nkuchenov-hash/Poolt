package app.poolt.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.poolt.core.Device
import app.poolt.core.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.URL

/**
 * SSDP cannot work on some Wi-Fi access points. Scan only the phone's
 * current Wi-Fi /24 for actual LG, Samsung and Roku control endpoints.
 * Never scan mobile data or a VPN range.
 */
object LanProbeDiscovery {
    data class WifiLan(val network: Network, val ip: Inet4Address, val prefix: Int)

    fun wifiLan(context: Context): WifiLan? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val links = cm.getLinkProperties(network) ?: continue
            val addr = links.linkAddresses.firstOrNull { it.address is Inet4Address } ?: continue
            return WifiLan(network, addr.address as Inet4Address, addr.prefixLength)
        }
        return null
    }

    suspend fun discover(context: Context): List<Device> = withContext(Dispatchers.IO) {
        val lan = wifiLan(context) ?: return@withContext emptyList()
        val own = lan.ip.address.map { it.toInt() and 0xff }
        if (own.size != 4 || own[0] == 127 || own[0] == 169 && own[1] == 254) {
            return@withContext emptyList()
        }
        val prefix = "${own[0]}.${own[1]}.${own[2]}."
        val gate = Semaphore(36)
        coroutineScope {
            (1..254).filter { it != own[3] }.map { last ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val ip = prefix + last
                        probeHost(lan.network, ip)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun probeHost(network: Network, ip: String): Device? {
        val candidates = listOf(
            3000 to "lg.webos", 3001 to "lg.webos",
            8001 to "samsung.smarttv", 8002 to "samsung.smarttv",
            8060 to "roku.ecp"
        )
        for ((port, driver) in candidates) {
            val open = runCatching {
                network.socketFactory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 260)
                }
                true
            }.getOrDefault(false)
            if (!open) continue

            // Unlike LG and Roku, port 8001 can be shared by unrelated LAN devices.
            // Check the Samsung API before labeling one as a Samsung TV.
            val model: String? = when (driver) {
                "samsung.smarttv" -> {
                    if (port == 8001) samsungIdentity(network, ip)
                    else "Samsung TV · соединение требует подтверждения"
                }
                else -> null
            }
            if (driver == "samsung.smarttv" && model == null) continue
            val brand = when (driver) {
                "lg.webos" -> "LG"
                "samsung.smarttv" -> "Samsung"
                else -> "Roku"
            }
            return Device(
                id = "$driver@$ip",
                name = if (model != null && !model.startsWith("Samsung TV")) "Samsung · $model"
                    else "$brand TV · $ip",
                type = if (driver == "roku.ecp") DeviceType.MEDIA_PLAYER else DeviceType.TV,
                driverId = driver, brand = brand,
                model = model ?: "Найден по сетевому порту $port",
                address = ip, isOnline = false
            )
        }
        return null
    }

    private fun samsungIdentity(network: Network, ip: String): String? = runCatching {
        val conn = (network.openConnection(URL("http://$ip:8001/api/v2/")) as HttpURLConnection).apply {
            connectTimeout = 450
            readTimeout = 450
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = conn.inputStream.bufferedReader().use { it.readText() }.take(15000)
            val root = JSONObject(body)
            val device = root.optJSONObject("device") ?: return@runCatching null
            device.optString("modelName").ifBlank { device.optString("name", "Samsung TV") }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
