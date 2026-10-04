package app.poolt.drivers

import app.poolt.core.*
import app.poolt.network.SsdpDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RokuDriver : DeviceDriver {
    override val id = "roku.ecp"
    override val version = 1
    private val client = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()

    override suspend fun discover(): List<Device> = SsdpDiscovery.discover().filter { it.driverId == id }

    override suspend fun pair(device: Device): Result<Unit> = runCatching {
        val ip = requireNotNull(device.address)
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url("http://" + ip + ":8060/query/device-info").build()).execute().use {
                if (!it.isSuccessful) error("Roku не отвечает: HTTP " + it.code)
            }
        }
    }

    override suspend fun send(device: Device, command: RemoteCommand): Result<Unit> = runCatching {
        val ip = requireNotNull(device.address)
        val key = mapKey(command) ?: error("Команда пока не поддерживается Roku")
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("http://" + ip + ":8060/keypress/" + key)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            client.newCall(request).execute().use {
                if (!it.isSuccessful) error("Roku HTTP " + it.code)
            }
        }
    }

    private fun mapKey(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "Power"
        RemoteCommand.HOME -> "Home"
        RemoteCommand.BACK -> "Back"
        RemoteCommand.UP -> "Up"
        RemoteCommand.DOWN -> "Down"
        RemoteCommand.LEFT -> "Left"
        RemoteCommand.RIGHT -> "Right"
        RemoteCommand.OK -> "Select"
        RemoteCommand.VOLUME_UP -> "VolumeUp"
        RemoteCommand.VOLUME_DOWN -> "VolumeDown"
        RemoteCommand.MUTE -> "VolumeMute"
        RemoteCommand.CHANNEL_UP -> "ChannelUp"
        RemoteCommand.CHANNEL_DOWN -> "ChannelDown"
        RemoteCommand.PLAY_PAUSE -> "Play"
        RemoteCommand.REWIND -> "Rev"
        RemoteCommand.FAST_FORWARD -> "Fwd"
        RemoteCommand.NUMBER_0 -> "Lit_0"
        RemoteCommand.NUMBER_1 -> "Lit_1"
        RemoteCommand.NUMBER_2 -> "Lit_2"
        RemoteCommand.NUMBER_3 -> "Lit_3"
        RemoteCommand.NUMBER_4 -> "Lit_4"
        RemoteCommand.NUMBER_5 -> "Lit_5"
        RemoteCommand.NUMBER_6 -> "Lit_6"
        RemoteCommand.NUMBER_7 -> "Lit_7"
        RemoteCommand.NUMBER_8 -> "Lit_8"
        RemoteCommand.NUMBER_9 -> "Lit_9"
        RemoteCommand.INFO -> "Info"
        else -> null
    }
}
