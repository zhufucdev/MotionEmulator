package com.zhufucdev.stub_plugin

import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.net.ssl.SSLContext

data class Server(val port: Int, val useTls: Boolean) {
    internal var previousConnection: HttpClient? = null
}

interface ServerScope {
    val emulation: Emulation
    suspend fun sendStarted(info: EmulationInfo)
    suspend fun sendProgress(intermediate: Intermediate)
}

interface ServerConnection {
    fun close()
}

suspend fun Server.connect(id: String, block: suspend ServerScope.() -> Unit): ServerConnection {
    val client =
        previousConnection
            ?.takeIf { it.engine.isActive }
            ?: HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json()
                }

                install(WebSockets) {
                    contentConverter = KotlinxWebsocketSerializationConverter(Json)
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

    client.webSocket(
        request = {
            url {
                protocol = if (useTls) URLProtocol.WSS else URLProtocol.WS
                host = "localhost"
                port = this@connect.port
                path("join")
            }
        },
        block = {
            send(Frame.Text(id))
            val received = receiveDeserialized<Emulation>()
            block.invoke(object : ServerScope {
                private var started = false
                override val emulation: Emulation
                    get() = received

                override suspend fun sendStarted(info: EmulationInfo) {
                    sendSerialized(info)
                    started = true
                }

                override suspend fun sendProgress(intermediate: Intermediate) {
                    if (!started) throw IllegalStateException("Sending progress before start")
                    sendSerialized(intermediate)
                }
            })
        }
    )

    return object : ServerConnection {
        override fun close() {
            client.close()
        }
    }
}
