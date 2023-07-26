package com.zhufucdev.motion_emulator.provider

import android.content.Context
import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.deserialize
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineEnvironmentBuilder
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.converter
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.collections.set

@Serializable
data class EmulationRef(
    val trace: String,
    val motion: String,
    val cells: String,
    val velocity: Double,
    val repeat: Int,
    val satelliteCount: Int
)

object Scheduler {
    private val stateListeners = mutableSetOf<(String, Boolean) -> Boolean>()
    private val intermediateListeners = mutableSetOf<(String, Intermediate) -> Unit>()

    private var mEmulation: Emulation? = null
    private var mInfo: MutableMap<String, EmulationInfo> = hashMapOf()
    private val mIntermediate: MutableMap<String, Intermediate> = hashMapOf()

    private var port = 20230
    private var tls = false
    private lateinit var server: ApplicationEngine
    private var serverRunning = false

    fun init(context: Context) {
        if (serverRunning) {
            Log.w("Scheduler", "Reinitialize a running instance")
            return
        }
        val prefs = context.sharedPreferences()
        port = prefs.getString("provider_port", "")!!.toIntOrNull() ?: 20230
        tls = prefs.getBoolean("provider_tls", true)
        server = embeddedServer(Netty, applicationEngineEnvironment {
            if (tls) {
                configureSsl(port)
            } else {
                configure(port)
            }

            module(Application::eventServer)
        })

        server.start(false)
        serverRunning = true

        Plugins.enabled.forEach {
            it.notifyStart(context)
        }
    }

    fun setIntermediate(id: String, info: Intermediate?) {
        if (info != null) {
            mIntermediate[id] = info
            intermediateListeners.forEach {
                try {
                    it.invoke(id, info)
                } catch (e: Exception) {
                    Log.w("Scheduler", "Error while notifying intermediate")
                    e.printStackTrace()
                }
            }
        } else {
            mIntermediate.remove(id)
        }
    }

    val intermediate: Map<String, Intermediate> get() = mIntermediate

    fun setInfo(id: String, info: EmulationInfo?) {
        if (info != null) {
            mInfo[id] = info
        } else {
            mInfo.remove(id)
        }
        notifyStateChanged(id, info != null)
    }

    private fun notifyStateChanged(id: String, state: Boolean) {
        stateListeners.removeAll {
            try {
                it.invoke(id, state)
            } catch (e: Exception) {
                Log.w("Scheduler", "Error while notifying changes to emulation info")
                e.printStackTrace()
                false
            }
        }
    }

    val info: Map<String, EmulationInfo> get() = mInfo

    var emulation: Emulation?
        set(value) {
            mEmulation = value
            if (value == null) {
                val original = mInfo
                mInfo = mutableMapOf()
                original.forEach { (id, _) ->
                    notifyStateChanged(id, false)
                }
            }
        }
        get() = mEmulation

    fun onEmulationStateChanged(l: (id: String, state: Boolean) -> Unit): ListenCallback {
        val actual: (String, Boolean) -> Boolean = { id, state ->
            l.invoke(id, state)
            false
        }
        stateListeners.add(actual)

        return object : ListenCallback {
            override fun pause() {
                if (!stateListeners.contains(actual)) {
                    throw IllegalStateException("Already paused")
                }

                stateListeners.remove(actual)
            }

            override fun resume() {
                if (stateListeners.contains(actual)) {
                    throw IllegalStateException("Already resumed")
                }

                stateListeners.add(actual)
            }
        }
    }

    suspend fun nextEmulationState(targetId: String? = null) = suspendCancellableCoroutine {
        stateListeners.add { target, b ->
            if (targetId == null || target == targetId) {
                it.resumeWith(Result.success(b))
                return@add true
            }
            false
        }
    }

    fun addIntermediateListener(l: (String, Intermediate) -> Unit): ListenCallback {
        intermediateListeners.add(l)

        return object : ListenCallback {
            override fun pause() {
                if (!intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already paused")
                }

                intermediateListeners.remove(l)
            }

            override fun resume() {
                if (intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already resumed")
                }

                intermediateListeners.add(l)
            }
        }
    }

    fun stop(context: Context) {
        Plugins.enabled.forEach { it.notifyStop(context) }
        server.stop()
        serverRunning = false
    }
}

interface ListenCallback {
    fun pause()
    fun resume()
}

fun ApplicationEngineEnvironmentBuilder.configureSsl(port: Int) {
    val keyAlis = "motion_provider"
    val keyPassword = NanoIdUtils.randomNanoId().toCharArray()
    val keyStore = generateSelfSignedKeyStore(keyAlis, keyPassword)

    sslConnector(
        keyStore = keyStore,
        privateKeyPassword = { keyPassword },
        keyStorePassword = { keyPassword },
        keyAlias = keyAlis
    ) {
        host = "127.0.0.1"
        this.port = port
    }
}

fun ApplicationEngineEnvironmentBuilder.configure(port: Int) {
    connector {
        host = "127.0.0.1"
        this.port = port
    }
}

fun Application.eventServer() {
    install(ContentNegotiation) {
        json()
    }

    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
        contentConverter = KotlinxWebsocketSerializationConverter(Json)
    }

    routing {
        suspend fun ApplicationCall.respondEmulation(emulation: Emulation?) {
            if (emulation == null) {
                respond(HttpStatusCode.NoContent)
            } else {
                respond(emulation)
            }
        }

        webSocketRaw("/join") {
            val id = (incoming.receive() as Frame.Text).readText()
            val watchdog = launch {
                while (true) {
                    val running = Scheduler.nextEmulationState(id)
                    if (!running) {
                        close(
                            CloseReason(
                                CloseReason.Codes.GOING_AWAY,
                                "emulation canceled"
                            )
                        )
                        break
                    }
                }
            }

            try {
                val info = receiveDeserialized<EmulationInfo>()
                Scheduler.setInfo(id, info)
                for (frame in incoming) {
                    if (frame is Frame.Close) {
                        break
                    }
                    val data = converter!!.deserialize<Intermediate>(frame)
                    Scheduler.setIntermediate(id, data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                watchdog.cancelAndJoin()
                Scheduler.setInfo(id, null)
                Scheduler.setIntermediate(id, null)
            }
        }

        get("/current") {
            // get the current emulation without blocking
            call.respondEmulation(Scheduler.emulation)
        }
    }
}