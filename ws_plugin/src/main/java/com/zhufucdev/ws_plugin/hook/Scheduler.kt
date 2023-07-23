package com.zhufucdev.ws_plugin.hook

import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.Method
import com.zhufucdev.xposed.AbstractScheduler
import com.zhufucdev.xposed.PREFERENCE_NAME_BRIDGE
import com.zhufucdev.xposed.TrustAllX509TrustManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.ws
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import kotlin.time.Duration.Companion.seconds

object Scheduler : AbstractScheduler() {
    private const val TAG = "Scheduler"
    private const val LOCALHOST = "localhost"
    private var port = 20230
    private var tls = false

    private val providerUrl get() = (if (tls) "https://" else "http://") + "$LOCALHOST:$port"

    private val httpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json()
            }

            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }

            engine {
                // disable certificate verification
                if (tls) {
                    sslManager = { connection ->
                        connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                            init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                        }.socketFactory
                    }
                }
                connectTimeout = 0
                socketTimeout = 0
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun PackageParam.initialize() {
        val prefs = prefs(PREFERENCE_NAME_BRIDGE)
        port = prefs.getString("me_server_port").toIntOrNull() ?: 2023
        tls = prefs.getBoolean("me_server_tls", true)
        hookingMethod =
            prefs.getString("me_method", "xposed_only").let {
                Method.valueOf(it.uppercase())
            }

        loggerI(tag = TAG, "service listens on $providerUrl")

        GlobalScope.launch {
            startServer()
        }
    }

    private lateinit var wsSession: DefaultClientWebSocketSession
    private suspend fun startServer() {
        var warned = false

        while (true) {
            httpClient.ws("${providerUrl}/join") {
                send(Frame.Text(id))
                val emulation = receiveDeserialized<Emulation>()
                wsSession = this // this is actually dangerous,
                // but I don't think there's way out
                startEmulation(emulation)
            }

            if (!warned) {
                loggerI(tag = TAG, msg = "Provider offline. Waiting for data channel to become online")
                warned = true
            }
            delay(1.seconds)
        }
    }

    override suspend fun notifyStarted(info: EmulationInfo) {
        wsSession.sendSerialized(info)
    }

    override suspend fun notifyStopped() {
        // handled by web socket lifecycle
    }

    override suspend fun notifyProgress(intermediate: Intermediate) {
        wsSession.sendSerialized(intermediate)
    }
}