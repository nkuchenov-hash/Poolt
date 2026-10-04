package app.poolt.drivers

import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand
import org.json.JSONObject

object DriverCatalogParser {
    fun parse(json: String): List<DriverPackage> {
        val root = JSONObject(json)
        val array = root.getJSONArray("drivers")
        val result = mutableListOf<DriverPackage>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val commandsObject = item.optJSONObject("commands") ?: JSONObject()
            val commands = buildMap {
                val keys = commandsObject.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val command = runCatching { RemoteCommand.valueOf(rawKey) }.getOrNull() ?: continue
                    val def = commandsObject.getJSONObject(rawKey)
                    put(
                        command,
                        CommandDefinition(
                            method = def.optString("method").takeIf { it.isNotBlank() },
                            path = def.optString("path").takeIf { it.isNotBlank() },
                            body = def.optString("body").takeIf { it.isNotBlank() },
                            payload = def.optString("payload").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }

            val discoveryObject = item.optJSONObject("discovery")
            val discovery = discoveryObject?.let {
                DiscoveryDefinition(
                    type = it.optString("type", "none"),
                    query = it.optString("query").takeIf { value -> value.isNotBlank() },
                    port = if (it.has("port")) it.optInt("port") else null
                )
            }

            result += DriverPackage(
                id = item.getString("id"),
                name = item.getString("name"),
                vendor = item.optString("vendor", "Community"),
                version = item.getInt("version"),
                minAppVersion = item.optInt("minAppVersion", 1),
                deviceType = DeviceType.valueOf(item.optString("deviceType", "OTHER")),
                transport = DriverTransport.valueOf(item.optString("transport", "NATIVE")),
                discovery = discovery,
                commands = commands
            )
        }

        return result
    }
}
