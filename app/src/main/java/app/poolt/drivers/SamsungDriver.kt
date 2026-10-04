package app.poolt.drivers

import android.content.Context
import android.util.Base64
import app.poolt.core.*
import app.poolt.network.SsdpDiscovery
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume

class SamsungDriver(private val context: Context) : DeviceDriver {
    override val id = "samsung.smarttv"
    override val version = 1
    private val prefs = context.getSharedPreferences("samsung_tokens", Context.MODE_PRIVATE)
    private val client = insecureClient()

    override suspend fun discover(): List<Device> = SsdpDiscovery.discover().filter { it.driverId == id }
    override suspend fun pair(device: Device): Result<Unit> = connect(device, null)
    override suspend fun send(device: Device, command: RemoteCommand): Result<Unit> {
        val key = keyFor(command) ?: return Result.failure(IllegalArgumentException("Команда не поддерживается Samsung"))
        return connect(device, key)
    }

    private suspend fun connect(device: Device, key: String?): Result<Unit> = suspendCancellableCoroutine { cont ->
        val ip = device.address
        if (ip == null) {
            cont.resume(Result.failure(IllegalArgumentException("Не указан IP телевизора")))
            return@suspendCancellableCoroutine
        }
        val token = prefs.getString(ip, null)
        val name = Base64.encodeToString("Poolt".toByteArray(), Base64.NO_WRAP)
        val url = "wss://" + ip + ":8002/api/v2/channels/samsung.remote.control?name=" + name +
            (token?.let { "&token=" + it } ?: "")
        var finished = false
        fun finish(result: Result<Unit>) {
            if (!finished) {
                finished = true
                if (cont.isActive) cont.resume(result)
            }
        }
        val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (obj.optString("event")) {
                    "ms.channel.connect" -> {
                        val newToken = obj.optJSONObject("data")?.optString("token")
                        if (!newToken.isNullOrBlank()) prefs.edit().putString(ip, newToken).apply()
                        if (key != null) {
                            val payload = JSONObject()
                                .put("method", "ms.remote.control")
                                .put("params", JSONObject()
                                    .put("Cmd", "Click")
                                    .put("DataOfCmd", key)
                                    .put("Option", "false")
                                    .put("TypeOfRemote", "SendRemoteKey"))
                            webSocket.send(payload.toString())
                        }
                        finish(Result.success(Unit))
                        webSocket.close(1000, "done")
                    }
                    "ms.channel.unauthorized" -> {
                        finish(Result.failure(IllegalStateException("Подтвердите Poolt на экране Samsung")))
                        webSocket.close(1000, "unauthorized")
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                finish(Result.failure(t))
            }
        })
        cont.invokeOnCancellation { ws.cancel() }
    }

    private fun keyFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.POWER -> "KEY_POWER"
        RemoteCommand.SOURCE -> "KEY_SOURCE"
        RemoteCommand.SETTINGS, RemoteCommand.MENU -> "KEY_MENU"
        RemoteCommand.HOME -> "KEY_HOME"
        RemoteCommand.BACK -> "KEY_RETURN"
        RemoteCommand.GUIDE -> "KEY_GUIDE"
        RemoteCommand.INFO -> "KEY_INFO"
        RemoteCommand.UP -> "KEY_UP"
        RemoteCommand.DOWN -> "KEY_DOWN"
        RemoteCommand.LEFT -> "KEY_LEFT"
        RemoteCommand.RIGHT -> "KEY_RIGHT"
        RemoteCommand.OK -> "KEY_ENTER"
        RemoteCommand.VOLUME_UP -> "KEY_VOLUP"
        RemoteCommand.VOLUME_DOWN -> "KEY_VOLDOWN"
        RemoteCommand.MUTE -> "KEY_MUTE"
        RemoteCommand.CHANNEL_UP -> "KEY_CHUP"
        RemoteCommand.CHANNEL_DOWN -> "KEY_CHDOWN"
        RemoteCommand.PLAY_PAUSE -> "KEY_PLAY"
        RemoteCommand.REWIND -> "KEY_REWIND"
        RemoteCommand.FAST_FORWARD -> "KEY_FF"
        RemoteCommand.NUMBER_0 -> "KEY_0"
        RemoteCommand.NUMBER_1 -> "KEY_1"
        RemoteCommand.NUMBER_2 -> "KEY_2"
        RemoteCommand.NUMBER_3 -> "KEY_3"
        RemoteCommand.NUMBER_4 -> "KEY_4"
        RemoteCommand.NUMBER_5 -> "KEY_5"
        RemoteCommand.NUMBER_6 -> "KEY_6"
        RemoteCommand.NUMBER_7 -> "KEY_7"
        RemoteCommand.NUMBER_8 -> "KEY_8"
        RemoteCommand.NUMBER_9 -> "KEY_9"
    }

    private fun insecureClient(): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), SecureRandom()) }
        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
