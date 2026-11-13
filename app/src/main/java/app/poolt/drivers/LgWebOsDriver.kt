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


    // LG webOS IME calls work only while a compatible TV text field is focused.
    // Wait for the service reply instead of treating a queued websocket send as success.
    suspend fun insertText(device: Device, text: String): Result<Unit> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Введите текст"))
        return imeRequest(device, "insertText", JSONObject().put("text", text).put("replace", false))
    }

    suspend fun deleteText(device: Device): Result<Unit> =
        imeRequest(device, "deleteCharacters", JSONObject().put("count", 1))

    suspend fun submitText(device: Device): Result<Unit> =
        imeRequest(device, "sendEnterKey", JSONObject())

    private suspend fun imeRequest(
        device: Device,
        action: String,
        payload: JSONObject
    ): Result<Unit> = kotlinx.coroutines.withTimeoutOrNull(10000L) {
        suspendCancellableCoroutine<Result<Unit>> { cont ->
            val ip = device.address
            if (ip.isNullOrBlank()) {
                cont.resume(Result.failure(IllegalArgumentException("Неизвестен адрес LG")))
                return@suspendCancellableCoroutine
            }
            val finished = AtomicBoolean(false)
            val requestId = "poolt_ime_" + System.nanoTime()
            val key = prefs.getString(ip, null)
            var requestSent = false
            val socket = client.newWebSocket(
                Request.Builder().url("ws://$ip:3000").build(),
                object : WebSocketListener() {
                    private fun finish(result: Result<Unit>, ws: WebSocket) {
                        if (finished.compareAndSet(false, true)) {
                            if (cont.isActive) cont.resume(result)
                            ws.close(1000, "IME complete")
                        }
                    }
                    override fun onOpen(ws: WebSocket, response: Response) {
                        ws.send(registerPayload(key).toString())
                    }
                    override fun onMessage(ws: WebSocket, text: String) {
                        val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                        if (data.optString("type") == "registered" && !requestSent) {
                            requestSent = true
                            val newKey = data.optJSONObject("payload")?.optString("client-key")
                            if (!newKey.isNullOrBlank()) prefs.edit().putString(ip, newKey).apply()
                            ws.send(
                                JSONObject()
                                    .put("type", "request")
                                    .put("id", requestId)
                                    .put("uri", "ssap://com.webos.service.ime/$action")
                                    .put("payload", payload).toString()
                            )
                        } else if (data.optString("id") == requestId) {
                            if (data.optString("type") == "error" ||
                                data.optJSONObject("payload")?.optBoolean("returnValue", true) == false) {
                                finish(Result.failure(IllegalStateException(
                                    data.optJSONObject("payload")?.optString("errorText")
                                        ?: data.optString("error", "LG отклонил ввод")
                                )), ws)
                            } else {
                                finish(Result.success(Unit), ws)
                            }
                        } else if (data.optString("type") == "error") {
                            finish(Result.failure(IllegalStateException(data.optString("error"))), ws)
                        }
                    }
                    override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                        finish(Result.failure(t), ws)
                    }
                }
            )
            cont.invokeOnCancellation { socket.cancel() }
        }
    } ?: Result.failure(IllegalStateException("LG не подтвердил ввод текста"))

    // Subscribe while the device is selected. TV emits changes when a text
    // field receives or loses focus, allowing phone keyboard to appear.
    private var imeMonitor: WebSocket? = null
    fun stopKeyboardMonitor() {
        imeMonitor?.close(1000, "switch TV")
        imeMonitor = null
    }

    fun watchKeyboard(device: Device, onFocus: (Boolean) -> Unit) {
        stopKeyboardMonitor()
        val ip = device.address ?: return
        val key = prefs.getString(ip, null)
        val socket = client.newWebSocket(
            Request.Builder().url("ws://$ip:3000").build(),
            object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    ws.send(registerPayload(key).toString())
                }
                override fun onMessage(ws: WebSocket, text: String) {
                    val data = runCatching { JSONObject(text) }.getOrNull() ?: return
                    if (data.optString("type") == "registered") {
                        data.optJSONObject("payload")?.optString("client-key")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { prefs.edit().putString(ip, it).apply() }
                        ws.send(
                            JSONObject().put("type", "subscribe")
                                .put("id", "poolt_ime_watch")
                                .put("uri", "ssap://com.webos.service.ime/registerRemoteKeyboard")
                                .put("payload", JSONObject().put("subscribe", true)).toString()
                        )
                    } else if (data.optString("id") == "poolt_ime_watch") {
                        val focus = data.optJSONObject("payload")
                            ?.optJSONObject("currentWidget")?.optBoolean("focus", false)
                        if (focus != null) onFocus(focus)
                    }
                }
                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    onFocus(false)
                }
            }
        )
        imeMonitor = socket
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
