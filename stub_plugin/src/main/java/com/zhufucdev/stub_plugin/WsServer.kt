package com.zhufucdev.stub_plugin

import android.util.Log
import com.zhufucdev.stub.AgentState
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub_plugin.coroutine.launchPausing
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.converter
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.InternalAPI
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.send
import io.ktor.websocket.serialization.sendSerializedBase
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Optional
import javax.net.ssl.SSLContext

data class WsServer(val host: String = "localhost", val port: Int, val useTls: Boolean) {
    internal var previousConnection: HttpClient? = null
}

interface ServerScope {
    val emulation: Optional<Emulation>
    suspend fun sendStarted(info: EmulationInfo)
    suspend fun sendProgress(intermediate: Intermediate)
    suspend fun close()
}

interface ServerConnection {
    val successful: Boolean
    fun close()
}

private fun HttpRequestBuilder.urlTo(
    server: WsServer,
    config: URLBuilder.() -> Unit
) {
    url {
        host = server.host
        port = server.port
        config()
    }
}

@OptIn(InternalAPI::class)
suspend fun WsServer.connect(id: String, block: suspend ServerScope.() -> Unit): ServerConnection {
    val client =
        previousConnection
            ?.takeIf { it.engine.isActive }
            ?: HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json()
                }

                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(Json)
                    pingInterval = 500
                }

                engine {
                    // disable certificate verification
                    if (useTls) {
                        val context = SSLContext.getInstance("SSL")
                        context.init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                        config {
                            sslSocketFactory(context.socketFactory, TrustAllX509TrustManager)
                            hostnameVerifier { _, _ -> true }
                        }
                    }
                }
            }

    val currentEmu = runCatching {
        client.get {
            urlTo(this@connect) {
                protocol = if (useTls) URLProtocol.HTTPS else URLProtocol.HTTP
                path("current")
            }
        }
    }

    if (currentEmu.isFailure) {
        return object : ServerConnection {
            override val successful: Boolean = false
            override fun close() {
                client.close()
            }
        }
    } else if (currentEmu.getOrThrow().status.let { !it.isSuccess() }) {
        block.invoke(object : ServerScope {
            override val emulation: Optional<Emulation>
                get() = Optional.empty()

            override suspend fun sendStarted(info: EmulationInfo) {
                throw NotImplementedError()
            }

            override suspend fun sendProgress(intermediate: Intermediate) {
                throw NotImplementedError()
            }

            override suspend fun close() {
                throw NotImplementedError()
            }
        })
    } else {
        val emulation = currentEmu.getOrNull()!!.body<Emulation>()

        client.webSocket(
            request = {
                urlTo(this@connect) {
                    protocol = if (useTls) URLProtocol.WSS else URLProtocol.WS
                    path("join", id)
                }
            },
            block = {
                val scope = object : ServerScope {
                    private var started = false
                    override val emulation = Optional.of(emulation)

                    override suspend fun sendStarted(info: EmulationInfo) {
                        sendSerializedBase<EmulationInfo>(info, converter!!, Charsets.UTF_8)
                        started = true
                    }

                    override suspend fun sendProgress(intermediate: Intermediate) {
                        if (!started) throw IllegalStateException("Sending progress before start")
                        sendSerialized(intermediate)
                    }

                    override suspend fun close() {
                        close(CloseReason(CloseReason.Codes.NORMAL, "canceled programmatically"))
                    }
                }

                fun launchWorker() = launchPausing {
                    block.invoke(scope)
                    send(byteArrayOf(Byte.MAX_VALUE)) // this is signal completion
                }

                try {
                    var worker = launchWorker()
                    for (req in incoming) {
                        when (val state = AgentState.values()[req.readBytes().first().toInt()]) {
                            AgentState.CANCELED -> {
                                worker.cancelAndJoin()
                            }

                            AgentState.PAUSED -> {
                                worker.pause()
                            }

                            AgentState.PENDING -> {
                                if (worker.isPaused) {
                                    worker.resume()
                                } else {
                                    if (!worker.isCancelled) worker.cancelAndJoin()
                                    worker = launchWorker()
                                }
                            }

                            else -> {
                                Log.w(
                                    "WsServer",
                                    "Watchdog received an illegal request: ${state.name}"
                                )
                            }
                        }
                    }
                } finally {
                    close()
                }
            }
        )
    }


    return object : ServerConnection {
        override val successful: Boolean = true
        override fun close() {
            client.close()
        }
    }
}