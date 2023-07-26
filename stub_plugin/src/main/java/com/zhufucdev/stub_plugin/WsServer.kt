package com.zhufucdev.stub_plugin

import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
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
import io.ktor.websocket.Frame
import io.ktor.websocket.serialization.sendSerializedBase
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
}

interface ServerConnection {
    fun close()
}

private fun HttpRequestBuilder.urlTo(
    server: WsServer,
    path: String,
    config: URLBuilder.() -> Unit
) {
    url {
        host = server.host
        port = server.port
        path(path)
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

    val currentEmu = client.get {
        urlTo(this@connect, "current") {
            protocol = if (useTls) URLProtocol.HTTPS else URLProtocol.HTTP
        }
    }

    if (!currentEmu.status.isSuccess()) {
        block.invoke(object : ServerScope {
            override val emulation: Optional<Emulation>
                get() = Optional.empty()

            override suspend fun sendStarted(info: EmulationInfo) {
                throw NotImplementedError()
            }

            override suspend fun sendProgress(intermediate: Intermediate) {
                throw NotImplementedError()
            }
        })
    } else {
        val emulation = currentEmu.body<Emulation>()

        client.webSocket(
            request = {
                urlTo(this@connect, "join") {
                    protocol = if (useTls) URLProtocol.WSS else URLProtocol.WS
                }
            },
            block = {
                send(Frame.Text(id))
                try {
                    block.invoke(object : ServerScope {
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
                    })
                } finally {
                    send(Frame.Close())
                }
            }
        )
    }

    return object : ServerConnection {
        override fun close() {
            client.close()
        }
    }
}
