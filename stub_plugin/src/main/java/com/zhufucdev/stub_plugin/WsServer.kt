package com.zhufucdev.stub_plugin

import android.util.Log
import com.zhufucdev.stub.AgentState
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub_plugin.coroutine.PausingJob
import com.zhufucdev.stub_plugin.coroutine.launchPausing
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.protobuf.protobuf
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.security.SecureRandom
import java.util.Optional
import javax.net.ssl.SSLContext
import kotlin.coroutines.CoroutineContext

data class WsServer(val host: String = "localhost", val port: Int, val useTls: Boolean) {
    internal var previousConnection: HttpClient? = null
}

interface ServerScope : CoroutineScope {
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

@OptIn(ExperimentalSerializationApi::class)
suspend fun WsServer.connect(id: String, block: suspend ServerScope.() -> Unit): ServerConnection {
    val client =
        previousConnection
            ?.takeIf { it.engine.isActive }
            ?: HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    protobuf()
                }

                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(ProtoBuf)
                    maxFrameSize = Long.MAX_VALUE
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
        val context = currentCoroutineContext()
        block.invoke(object : ServerScope {
            override val coroutineContext: CoroutineContext = context

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
                try {
                    var worker = launchWorker(emulation, block)
                    while (true) {
                        when (val state = receiveCommand()) {
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
                                    worker = launchWorker(emulation, block)
                                }
                            }

                            AgentState.NOT_JOINED -> {
                                if (!worker.isCancelled) {
                                    worker.cancel()
                                    break
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
                } catch (e: Exception) {
                    Log.w("WsServer", "Error in connection to a websocket server", e)
                } finally {
                    close()
                    Log.d("WsServer", "Connection closed")
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

private suspend fun WebSocketSession.receiveCommand(): AgentState {
    val req = incoming.receive()
    return AgentState.values()[req.readBytes().first().toInt()]
}

private fun DefaultClientWebSocketSession.launchWorker(
    emulation: Emulation,
    block: suspend (ServerScope) -> Unit
): PausingJob = launchPausing {
    val context = currentCoroutineContext()
    val scope = object : ServerScope {
        private var started = false
        override val emulation = Optional.of(emulation)

        override val coroutineContext: CoroutineContext
            get() = context

        override suspend fun sendStarted(info: EmulationInfo) {
            sendSerialized(info)
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

    try {
        block.invoke(scope)
        send(byteArrayOf(0x7f)) // this is signal completion
    } catch (e: Exception) {
        send(byteArrayOf(-0x7f)) // this is signal failure
        Log.w("WsServer", "Worker finished exceptionally", e)
    }
}