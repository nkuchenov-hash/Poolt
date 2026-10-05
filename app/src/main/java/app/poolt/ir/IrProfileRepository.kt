package app.poolt.ir

import android.content.Context
import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class IrProfileRepository(private val context: Context) {
    companion object {
        const val DEFAULT_URL =
            "https://raw.githubusercontent.com/nkuchenov-hash/Poolt/main/ir/catalog.json"
        private const val PREFS = "ir_catalog"
        private const val KEY_JSON = "json"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cached(): List<IrProfile> {
        val json = prefs.getString(KEY_JSON, null)
            ?: context.assets.open("ir_catalog.json").bufferedReader().use { it.readText() }
        return parse(json)
    }

    fun refresh(url: String = DEFAULT_URL): Result<List<IrProfile>> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode !in 200..299) error("IR catalog HTTP " + connection.responseCode)
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val profiles = parse(json)
            prefs.edit().putString(KEY_JSON, json).apply()
            profiles
        } finally {
            connection.disconnect()
        }
    }

    fun parse(json: String): List<IrProfile> {
        val root = JSONObject(json)
        val profiles = root.getJSONArray("profiles")
        return buildList {
            for (i in 0 until profiles.length()) {
                val item = profiles.getJSONObject(i)
                val commandsObj = item.getJSONObject("commands")
                val commands = buildMap {
                    val keys = commandsObj.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        val command = runCatching { RemoteCommand.valueOf(name) }.getOrNull() ?: continue
                        val raw = commandsObj.get(name)
                        val value = when (raw) {
                            is Number -> raw.toInt()
                            is String -> if (raw.startsWith("0x", true)) raw.substring(2).toInt(16) else raw.toInt()
                            else -> continue
                        }
                        put(command, value)
                    }
                }
                add(
                    IrProfile(
                        id = item.getString("id"),
                        brand = item.getString("brand"),
                        model = item.getString("model"),
                        deviceType = DeviceType.valueOf(item.optString("deviceType", "TV")),
                        frequency = item.optInt("frequency", 38000),
                        protocol = IrProtocolType.valueOf(item.getString("protocol")),
                        address = parseInt(item.opt("address")),
                        commands = commands
                    )
                )
            }
        }
    }

    private fun parseInt(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> if (value.startsWith("0x", true)) value.substring(2).toInt(16) else value.toInt()
        else -> 0
    }
}
