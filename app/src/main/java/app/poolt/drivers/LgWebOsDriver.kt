package app.poolt.drivers

import android.content.Context
import app.poolt.core.*
import app.poolt.network.SsdpDiscovery
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class LgWebOsDriver(private val context: Context) : DeviceDriver {
    override val id = "lg.webos"
    override val version = 1
    private val prefs = context.getSharedPreferences("lg_webos_keys", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()

    override suspend fun discover(): List<Device> = SsdpDiscovery.discover().filter { it.driverId == id }
    override suspend fun pair(device: Device): Result<Unit> = withRegisteredSocket(device) { ws, _ -> ws.close(1000, "paired") }

    override suspend fun send(device: Device, command: RemoteCommand): Result<Unit> {
        val direct = directUri(command)
        if (direct != null) {
            return withRegisteredSocket(device) { ws, requestId ->
                val req = JSONObject().put("id", requestId).put("type", "request").put("uri", direct.first)
                direct.second?.let { req.put("payload", it) }
                ws.send(req.toString())
                ws.close(1000, "sent")
            }
        }
        val button = pointerButton(command)
            ?: return Result.failure(IllegalArgumentException("Команда не поддерживается LG"))
        return sendPointer(device, button)
    }

    private suspend fun sendPointer(device: Device, button: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        val ip = device.address
        if (ip == null) {
            cont.resume(Result.failure(IllegalArgumentException("Не указан IP телевизора")))
            return@suspendCancellableCoroutine
        }
        var finished = false
        fun finish(r: Result<Unit>) {
            if (!finished) {
                finished = true
                if (cont.isActive) cont.resume(r)
            }
        }
        val key = prefs.getString(ip, null)
        val ws = client.newWebSocket(Request.Builder().url("ws://" + ip + ":3000").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registerPayload(key).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                when {
                    obj.optString("type") == "registered" -> {
                        val newKey = obj.optJSONObject("payload")?.optString("client-key")
                        if (!newKey.isNullOrBlank()) prefs.edit().putString(ip, newKey).apply()
                        webSocket.send(JSONObject()
                            .put("id", "pointer")
                            .put("type", "request")
                            .put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
                            .toString())
                    }
                    obj.optString("id") == "pointer" -> {
                        val socketPath = obj.optJSONObject("payload")?.optString("socketPath")
                        if (socketPath.isNullOrBlank()) {
                            finish(Result.failure(IllegalStateException("LG не вернул pointer socket")))
                            return
                        }
                        client.newWebSocket(Request.Builder().url(socketPath).build(), object : WebSocketListener() {
                            override fun onOpen(pointerWs: WebSocket, response: Response) {
                                pointerWs.send("type:button\nname:" + button + "\n\n")
                                pointerWs.close(1000, "sent")
                                webSocket.close(1000, "sent")
                                finish(Result.success(Unit))
                            }
                            override fun onFailure(pointerWs: WebSocket, t: Throwable, response: Response?) {
                                finish(Result.failure(t))
                            }
                        })
                    }
                    obj.optString("type") == "error" -> finish(Result.failure(IllegalStateException(obj.optString("error"))))
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(Result.failure(t))
            }
        })
        cont.invokeOnCancellation { ws.cancel() }
    }

    private suspend fun withRegisteredSocket(
        device: Device,
        action: (WebSocket, String) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        val ip = device.address
        if (ip == null) {
            cont.resume(Result.failure(IllegalArgumentException("Не указан IP телевизора")))
            return@suspendCancellableCoroutine
        }
        var finished = false
        fun finish(r: Result<Unit>) {
            if (!finished) {
                finished = true
                if (cont.isActive) cont.resume(r)
            }
        }
        val key = prefs.getString(ip, null)
        val ws = client.newWebSocket(Request.Builder().url("ws://" + ip + ":3000").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registerPayload(key).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (obj.optString("type")) {
                    "registered" -> {
                        val newKey = obj.optJSONObject("payload")?.optString("client-key")
                        if (!newKey.isNullOrBlank()) prefs.edit().putString(ip, newKey).apply()
                        action(webSocket, "poolt-" + System.nanoTime())
                        finish(Result.success(Unit))
                    }
                    "error" -> finish(Result.failure(IllegalStateException(obj.optString("error", "LG pairing error"))))
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(Result.failure(t))
            }
        })
        cont.invokeOnCancellation { ws.cancel() }
    }

    private fun registerPayload(clientKey: String?): JSONObject {
        val permissions = JSONArray(listOf(
            "LAUNCH","LAUNCH_WEBAPP","APP_TO_APP","CLOSE","CONTROL_AUDIO","CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_POWER","READ_APP_STATUS",
            "READ_CURRENT_CHANNEL","READ_INPUT_DEVICE_LIST","READ_NETWORK_STATE","READ_RUNNING_APPS",
            "READ_TV_CHANNEL_LIST","WRITE_NOTIFICATION_TOAST","CONTROL_TV","CONTROL_INPUT_TV","READ_POWER_STATE"
        ))
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "0.2.0")
            .put("permissions", permissions)
        val payload = JSONObject().put("pairingType", "PROMPT").put("manifest", manifest)
        if (!clientKey.isNullOrBlank()) payload.put("client-key", clientKey)
        return JSONObject().put("id", "register_0").put("type", "register").put("payload", payload)
    }

    private fun directUri(command: RemoteCommand): Pair<String, JSONObject?>? = when (command) {
        RemoteCommand.POWER -> "ssap://system/turnOff" to null
        RemoteCommand.VOLUME_UP -> "ssap://audio/volumeUp" to null
        RemoteCommand.VOLUME_DOWN -> "ssap://audio/volumeDown" to null
        RemoteCommand.MUTE -> "ssap://audio/setMute" to JSONObject().put("mute", true)
        RemoteCommand.CHANNEL_UP -> "ssap://tv/channelUp" to null
        RemoteCommand.CHANNEL_DOWN -> "ssap://tv/channelDown" to null
        RemoteCommand.PLAY_PAUSE -> "ssap://media.controls/play" to null
        RemoteCommand.REWIND -> "ssap://media.controls/rewind" to null
        RemoteCommand.FAST_FORWARD -> "ssap://media.controls/fastForward" to null
        RemoteCommand.HOME -> "ssap://system.launcher/launch" to JSONObject().put("id", "com.webos.app.home")
        else -> null
    }

    private fun pointerButton(command: RemoteCommand): String? = when (command) {
        RemoteCommand.UP -> "UP"
        RemoteCommand.DOWN -> "DOWN"
        RemoteCommand.LEFT -> "LEFT"
        RemoteCommand.RIGHT -> "RIGHT"
        RemoteCommand.OK -> "ENTER"
        RemoteCommand.BACK -> "BACK"
        RemoteCommand.MENU -> "MENU"
        else -> null
    }
}
