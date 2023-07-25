package com.zhufucdev.motion_emulator.provider

import android.content.Context
import android.util.Log
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.motion_emulator.extension.lazySharedPreferences
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.plugin.Plugins
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.reflect.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
import kotlin.coroutines.suspendCoroutine

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
    private val emulationQueue = mutableMapOf<String, (Emulation?) -> Unit>()
    private val stateListeners = mutableSetOf<(String, Boolean) -> Unit>()
    private val intermediateListeners = mutableSetOf<(String, Intermediate) -> Unit>()

    private var mEmulation: Emulation? = null
    private val mInfo: MutableMap<String, EmulationInfo> = hashMapOf()
    private val mIntermediate: MutableMap<String, Intermediate> = hashMapOf()

    private var port = 20230
    private var tls = false
    private lateinit var server: ApplicationEngine
    private var serverRunning = false

    private val environment
        get() =
            applicationEngineEnvironment {
                if (tls) {
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
                        port = this@Scheduler.port
                    }
                } else {
                    connector {
                        host = "127.0.0.1"
                        port = this@Scheduler.port
                    }
                }

                module(Application::eventServer)
            }

    fun init(context: Context) {
        if (serverRunning) {
            Log.w("Scheduler", "Reinitialize a running instance")
            return
        }
        val prefs = context.sharedPreferences()
        port = prefs.getString("provider_port", "")!!.toIntOrNull() ?: 20230
        tls = prefs.getBoolean("provider_tls", true)
        server = embeddedServer(Netty, environment)

        server.start(false)
        serverRunning = true

        Plugins.enabled.forEach {
            it.notifyStart(context)
        }
    }

    fun setIntermediate(id: String, info: Intermediate?) {
        if (info != null) {
            mIntermediate[id] = info
            intermediateListeners.forEach { it.invoke(id, info) }
        } else {
            mIntermediate.remove(id)
        }
    }

    val intermediate: Map<String, Intermediate> get() = mIntermediate

    fun setInfo(id: String, info: EmulationInfo?) {
        if (info != null) {
            mInfo[id] = info
            Log.d("Scheduler", "info[$id] updated with $info")
        } else {
            mInfo.remove(id)
            emulationQueue[id]?.invoke(null)
            emulationQueue.remove(id)
            Log.d("Scheduler", "info[$id] has been removed")
        }
        stateListeners.forEach { it.invoke(id, info != null) }
    }

    val info: Map<String, EmulationInfo> get() = mInfo

    var emulation: Emulation?
        set(value) {
            mEmulation = value
            if (value == null) {
                mInfo.clear()
            }
            emulationQueue.forEach { (_, queue) -> queue.invoke(value) }
            emulationQueue.clear()
        }
        get() = mEmulation

    suspend fun next(id: String): Emulation? = suspendCoroutine { c ->
        emulationQueue[id] = { e ->
            c.resumeWith(Result.success(e))
        }
    }

    fun onEmulationStateChanged(l: (String, Boolean) -> Unit): ListenCallback {
        stateListeners.add(l)

        return object : ListenCallback {
            override fun cancel() {
                if (!stateListeners.contains(l)) {
                    throw IllegalStateException("Already cancelled")
                }

                stateListeners.remove(l)
            }

            override fun resume() {
                if (stateListeners.contains(l)) {
                    throw IllegalStateException("Already resumed")
                }

                stateListeners.add(l)
            }
        }
    }

    fun addIntermediateListener(l: (String, Intermediate) -> Unit): ListenCallback {
        intermediateListeners.add(l)

        return object : ListenCallback {
            override fun cancel() {
                if (!intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already cancelled")
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
    fun cancel()
    fun resume()
}

fun Application.eventServer() {
    install(ContentNegotiation) {
        json()
    }

    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 15_000
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

        webSocket("/join") {
            val localEmulation = Scheduler.emulation
            if (localEmulation == null) {
                call.respond(HttpStatusCode.NoContent)
                return@webSocket
            }
            val id = (incoming.receive() as Frame.Text).readText()
            sendSerialized(Scheduler.emulation)
            val info = receiveDeserialized<EmulationInfo>()
            Scheduler.setInfo(id, info)

            try {
                incoming.consumeAsFlow().collect {
                    val data = converter!!.deserialize(
                        Charset.defaultCharset(),
                        typeInfo<Intermediate>(),
                        it
                    )
                    if (data is Intermediate) {
                        Scheduler.setIntermediate(id, data)
                    }
                }
            } finally {
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