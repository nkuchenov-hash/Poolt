package app.poolt.ir

import android.content.Context
import app.poolt.core.DeviceType
import app.poolt.core.RemoteCommand
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class IrProfileRepository(private val context: Context) {
    companion object {
        const val DEFAULT_URL =
            "https://raw.githubusercontent.com/nkuchenov-hash/Poolt/main/ir/catalog.json"
        const val IRDB_INDEX =
            "https://cdn.jsdelivr.net/gh/probonopd/irdb@master/codes/index"
        const val IRDB_CODES =
            "https://cdn.jsdelivr.net/gh/probonopd/irdb@master/codes/"
        private const val PREFS = "ir_catalog"
        private const val KEY_JSON = "json"
        private const val KEY_IRDB_INDEX = "irdb_index"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cached(): List<IrProfile> {
        val json = prefs.getString(KEY_JSON, null)
            ?: context.assets.open("ir_catalog.json").bufferedReader().use { it.readText() }
        return parse(json)
    }

    fun refresh(url: String = DEFAULT_URL): Result<List<IrProfile>> = runCatching {
        val json = fetchText(url)
        val profiles = parse(json)
        prefs.edit().putString(KEY_JSON, json).apply()
        profiles
    }

    fun cachedIrdbIndex(): List<IrCatalogEntry> =
        prefs.getString(KEY_IRDB_INDEX, null)?.let(::parseIrdbIndex).orEmpty()

    fun fetchIrdbIndex(): Result<List<IrCatalogEntry>> = runCatching {
        val text = fetchText(IRDB_INDEX, 12000)
        prefs.edit().putString(KEY_IRDB_INDEX, text).apply()
        parseIrdbIndex(text)
    }

    fun fetchIrdbProfile(entry: IrCatalogEntry): Result<IrProfile> = runCatching {
        val encodedPath = entry.path.split("/").joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        val csv = fetchText(IRDB_CODES + encodedPath, 8000)
        parseIrdbCsv(entry, csv)
    }

    private fun fetchText(url: String, timeout: Int = 5000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeout
            readTimeout = timeout
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Poolt-Android")
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP " + connection.responseCode)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    fun parseIrdbIndex(text: String): List<IrCatalogEntry> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.endsWith(".csv", true) }
            .mapNotNull { path ->
                val parts = path.split("/")
                if (parts.size < 3) return@mapNotNull null
                IrCatalogEntry(
                    path = path,
                    brand = parts.first(),
                    deviceType = parts.subList(1, parts.size - 1).joinToString(" / "),
                    codeSet = parts.last().removeSuffix(".csv")
                )
            }
            .distinctBy { it.path }
            .sortedWith(compareBy<IrCatalogEntry> { it.brand.lowercase() }.thenBy { it.deviceType.lowercase() })
            .toList()

    private fun parseIrdbCsv(entry: IrCatalogEntry, csv: String): IrProfile {
        val rows = csv.lineSequence().drop(1).map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (rows.isEmpty()) error("Пустой IR-код-сет")

        var protocol: IrProtocolType? = null
        var device = 0
        var subDevice = -1
        val commands = linkedMapOf<RemoteCommand, Int>()

        for (line in rows) {
            val p = line.split(",")
            if (p.size < 5) continue
            val command = mapFunction(p[0]) ?: continue
            val proto = mapProtocol(p[1]) ?: continue
            val dev = p[2].trim().toIntOrNull() ?: continue
            val sub = p[3].trim().toIntOrNull() ?: -1
            val function = p[4].trim().toIntOrNull() ?: continue

            if (protocol == null) {
                protocol = proto
                device = dev
                subDevice = sub
            }
            if (proto == protocol) commands.putIfAbsent(command, function)
        }

        val resolved = protocol ?: error("Протокол этого код-сета пока не поддерживается")
        if (commands.isEmpty()) error("В код-сете нет совместимых кнопок")

        return IrProfile(
            id = "irdb:" + entry.path,
            brand = entry.brand,
            model = entry.deviceType + " · " + entry.codeSet,
            deviceType = mapDeviceType(entry.deviceType),
            frequency = 38000,
            protocol = resolved,
            address = device,
            subAddress = subDevice,
            commands = commands
        )
    }

    private fun mapProtocol(name: String): IrProtocolType? = when (name.trim().uppercase()) {
        "NEC1", "NEC2", "NEC" -> IrProtocolType.NEC
        "NECX1", "NECX2" -> IrProtocolType.NEC_EXT
        "SAMSUNG20", "SAMSUNG32", "SAMSUNG" -> IrProtocolType.SAMSUNG32
        "SONY12" -> IrProtocolType.SONY12
        "SONY15" -> IrProtocolType.SONY15
        "SONY20" -> IrProtocolType.SONY20
        else -> null
    }

    private fun mapFunction(raw: String): RemoteCommand? {
        val n = raw.trim().uppercase()
            .replace("_", " ")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")
        return when (n) {
            "POWER", "POWER TOGGLE", "ON/OFF", "ON OFF" -> RemoteCommand.POWER
            "INPUT", "INPUT SOURCE", "SOURCE", "TV/VIDEO" -> RemoteCommand.SOURCE
            "SETTINGS", "SETUP" -> RemoteCommand.SETTINGS
            "HOME", "SMART HUB" -> RemoteCommand.HOME
            "BACK", "RETURN", "EXIT" -> RemoteCommand.BACK
            "MENU", "TOOLS" -> RemoteCommand.MENU
            "GUIDE", "EPG" -> RemoteCommand.GUIDE
            "INFO", "DISPLAY" -> RemoteCommand.INFO
            "UP", "CURSOR UP" -> RemoteCommand.UP
            "DOWN", "CURSOR DOWN" -> RemoteCommand.DOWN
            "LEFT", "CURSOR LEFT" -> RemoteCommand.LEFT
            "RIGHT", "CURSOR RIGHT" -> RemoteCommand.RIGHT
            "OK", "ENTER", "SELECT" -> RemoteCommand.OK
            "VOLUME +", "VOL +", "VOL+", "VOLUME UP" -> RemoteCommand.VOLUME_UP
            "VOLUME -", "VOL -", "VOL-", "VOLUME DOWN" -> RemoteCommand.VOLUME_DOWN
            "MUTE" -> RemoteCommand.MUTE
            "CHANNEL +", "CH +", "CH+", "CHANNEL UP", "CH NEXT" -> RemoteCommand.CHANNEL_UP
            "CHANNEL -", "CH -", "CH-", "CHANNEL DOWN", "CH PREV" -> RemoteCommand.CHANNEL_DOWN
            "PLAY", "PAUSE", "PLAY PAUSE" -> RemoteCommand.PLAY_PAUSE
            "REW", "REWIND" -> RemoteCommand.REWIND
            "FF", "FAST FORWARD", "FORWARD" -> RemoteCommand.FAST_FORWARD
            "0" -> RemoteCommand.NUMBER_0
            "1" -> RemoteCommand.NUMBER_1
            "2" -> RemoteCommand.NUMBER_2
            "3" -> RemoteCommand.NUMBER_3
            "4" -> RemoteCommand.NUMBER_4
            "5" -> RemoteCommand.NUMBER_5
            "6" -> RemoteCommand.NUMBER_6
            "7" -> RemoteCommand.NUMBER_7
            "8" -> RemoteCommand.NUMBER_8
            "9" -> RemoteCommand.NUMBER_9
            else -> null
        }
    }

    private fun mapDeviceType(raw: String): DeviceType {
        val s = raw.uppercase()
        return when {
            "TV" in s -> DeviceType.TV
            "PROJECTOR" in s -> DeviceType.PROJECTOR
            "AIR" in s && ("CONDITION" in s || "AC" == s) -> DeviceType.AIR_CONDITIONER
            "FAN" in s -> DeviceType.FAN
            "SOUNDBAR" in s -> DeviceType.SOUNDBAR
            "RECEIVER" in s || "AMPLIFIER" in s || "AUDIO" in s -> DeviceType.AUDIO
            "DVD" in s || "BLU" in s -> DeviceType.MEDIA_PLAYER
            "SAT" in s || "CABLE" in s || "SET TOP" in s || "CONVERTER" in s -> DeviceType.SET_TOP_BOX
            else -> DeviceType.OTHER
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
                        deviceType = runCatching { DeviceType.valueOf(item.optString("deviceType", "TV")) }.getOrDefault(DeviceType.OTHER),
                        frequency = item.optInt("frequency", 38000),
                        protocol = IrProtocolType.valueOf(item.getString("protocol")),
                        address = parseInt(item.opt("address")),
                        subAddress = parseInt(item.opt("subAddress")),
                        commands = commands
                    )
                )
            }
        }
    }

    private fun parseInt(value: Any?): Int = when (value) {
        is Number -> value.toInt()
        is String -> if (value.startsWith("0x", true)) value.substring(2).toInt(16) else value.toInt()
        else -> -1
    }
}
