package app.poolt.drivers

import android.content.Context
import app.poolt.core.*
import app.poolt.network.SsdpDiscovery
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
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

    // Keep the pointer input socket alive between moves. Re-opening it for every
    // button caused sluggish response and made true Magic Remote behaviour impossible.
    private val pointerMutex = Mutex()
    @Volatile private var activePointerIp: String? = null
    @Volatile private var pointerSocket: WebSocket? = null
    @Volatile private var pointerControlSocket: WebSocket? = null

    suspend fun movePointer(device: Device, dx: Int, dy: Int): Result<Unit> =
        pointerCommand(device, "type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")

    suspend fun clickPointer(device: Device): Result<Unit> =
        pointerCommand(device, "type:click\n\n")

    suspend fun scrollPointer(device: Device, dx: Int, dy: Int): Result<Unit> =
        pointerCommand(device, "type:scroll\ndx:$dx\ndy:$dy\n\n")

    suspend fun probePointer(device: Device): Result<Unit> = pointerMutex.withLock {
        val ip = device.address ?: return@withLock Result.failure(
            IllegalArgumentException("Не найден адрес телевизора")
        )
        if (pointerSocket != null && activePointerIp == ip) return@withLock Result.success(Unit)
        pointerSocket?.close(1000, "recheck")
        pointerControlSocket?.close(1000, "recheck")
        pointerSocket = null
        activePointerIp = null
        establishPointer(device)
    }

    private suspend fun sendPointer(device: Device, button: String): Result<Unit> =
        pointerCommand(device, "type:button\nname:$button\n\n")

    private suspend fun pointerCommand(device: Device, packet: String): Result<Unit> =
        pointerMutex.withLock {
            val ip = device.address ?: return@withLock Result.failure(
                IllegalArgumentException("Не найден адрес телевизора")
            )
            if (pointerSocket == null || activePointerIp != ip) {
                pointerSocket?.close(1000, "switch TV")
                pointerControlSocket?.close(1000, "switch TV")
                pointerSocket = null
                activePointerIp = null
                val connected = establishPointer(device)
                if (connected.isFailure) return@withLock connected
            }
            val ws = pointerSocket ?: return@withLock Result.failure(
                IllegalStateException("LG не открыл управление курсором")
            )
            if (ws.send(packet)) Result.success(Unit)
            else {
                pointerSocket = null
                activePointerIp = null
                Result.failure(IllegalStateException("Курсор отключился. Повторите действие."))
            }
        }

    private suspend fun establishPointer(device: Device): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val ip = device.address
            if (ip == null) {
                cont.resume(Result.failure(IllegalArgumentException("Не найден IP LG")))
                return@suspendCancellableCoroutine
            }
            val finished = AtomicBoolean(false)
            fun finish(value: Result<Unit>) {
                if (finished.compareAndSet(false, true) && cont.isActive) cont.resume(value)
            }
            val key = prefs.getString(ip, null)
            val control = client.newWebSocket(
                Request.Builder().url("ws://$ip:3000").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(registerPayload(key).toString())
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                        when {
                            obj.optString("type") == "registered" -> {
                                val newKey = obj.optJSONObject("payload")?.optString("client-key")
                                if (!newKey.isNullOrBlank()) prefs.edit().putString(ip, newKey).apply()
                                webSocket.send(
                                    JSONObject()
                                        .put("id", "poolt_pointer")
                                        .put("type", "request")
                                        .put("uri", "ssap://com.webos.service.networkinput/getPointerInputSocket")
                                        .toString()
                                )
                            }
                            obj.optString("id") == "poolt_pointer" -> {
                                val url = obj.optJSONObject("payload")?.optString("socketPath")
                                if (url.isNullOrBlank()) {
                                    finish(Result.failure(IllegalStateException("LG не разрешил управление курсором")))
                                    return
                                }
                                client.newWebSocket(
                                    Request.Builder().url(url).build(),
                                    object : WebSocketListener() {
                                        override fun onOpen(pointer: WebSocket, response: Response) {
                                            pointerControlSocket = webSocket
                                            pointerSocket = pointer
                                            activePointerIp = ip
                                            finish(Result.success(Unit))
                                        }
                                        override fun onClosed(pointer: WebSocket, code: Int, reason: String) {
                                            if (pointerSocket == pointer) {
                                                pointerSocket = null
                                                activePointerIp = null
                                            }
                                        }
                                        override fun onFailure(pointer: WebSocket, t: Throwable, response: Response?) {
                                            if (pointerSocket == pointer) {
                                                pointerSocket = null
                                                activePointerIp = null
                                            }
                                            finish(Result.failure(t))
                                        }
                                    }
                                )
                            }
                            obj.optString("type") == "error" -> finish(
                                Result.failure(IllegalStateException(obj.optString("error", "Ошибка LG")))
                            )
                        }
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        finish(Result.failure(t))
                    }
                }
            )
            cont.invokeOnCancellation { control.cancel() }
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
            "CONTROL_INPUT_JOYSTICK","CONTROL_MOUSE_AND_KEYBOARD","CONTROL_INPUT_MEDIA_PLAYBACK","CONTROL_POWER","READ_APP_STATUS",
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
