package com.zhufucdev.motion_emulator.provider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.factory.prefs
import com.zhufucdev.data.BROADCAST_AUTHORITY
import com.zhufucdev.data.Emulation
import com.zhufucdev.data.EmulationInfo
import com.zhufucdev.data.Intermediate
import com.zhufucdev.motion_emulator.BuildConfig
import com.zhufucdev.motion_emulator.lazySharedPreferences
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
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

    private var providerPort = 2023
    private var providerTls = false
    private lateinit var server: ApplicationEngine
    private var serverRunning = false

    private val environment
        get() =
            applicationEngineEnvironment {
                if (providerTls) {
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
                        port = providerPort
                    }
                } else {
                    connector {
                        host = "127.0.0.1"
                        port = providerPort
                    }
                }

                module(Application::eventServer)
            }

    fun init(context: Context) {
        if (serverRunning) {
            Log.w("Schedular", "Reinitialize a running instance")
            return
        }
        val prefs = context.prefs()
        providerPort = prefs.getString("provider_port").toIntOrNull() ?: 2023
        providerTls = prefs.getBoolean("provider_tls", true)
        server = embeddedServer(Netty, environment)

        server.start(false)
        serverRunning = true

        handlePlugins(context)
    }

    private fun handlePlugins(context: Context) {
        val pluginPackage = "${BuildConfig.APPLICATION_ID}.mock_location_plugin"
        try {
            context.packageManager.getApplicationInfo(pluginPackage, PackageManager.MATCH_ALL)
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }

        if (context.lazySharedPreferences().value.getBoolean("use_test_provider", false)) {
            context.sendBroadcast(Intent("$BROADCAST_AUTHORITY.EMULATION_START").apply {
                component = ComponentName(pluginPackage, "$pluginPackage.EmulationBroadcastReceiver")
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra("port", providerPort)
                putExtra("tls", providerTls)
            })
            Log.i("Schedular", "broadcast sent")
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
        server.stop()
        serverRunning = false
        context.sendBroadcast(Intent().apply {
            action = "$BROADCAST_AUTHORITY.EMULATION_STOP"
        })
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

    routing {
        suspend fun ApplicationCall.respondEmulation(emulation: Emulation?) {
            if (emulation == null) {
                respond(HttpStatusCode.NoContent)
            } else {
                respond(emulation)
            }
        }

        get("/current") {
            // get the current emulation without blocking
            call.respondEmulation(Scheduler.emulation)
        }

        get("/next/{id}") {
            // use the blocking method to query the next emulation change
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val next = Scheduler.next(id)
            call.respondEmulation(next)
        }

        post("/intermediate/{id}") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            Scheduler.setIntermediate(id, call.receive<Intermediate>().also {
                Log.d("EmulationApp", "coord = ${it.location.coordinateSystem}")
            })
            call.respond(HttpStatusCode.OK)
        }

        post("/state/{id}/running") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            Log.d("Provider", "$id is running")
            Scheduler.setInfo(id, call.receive())
            call.respond(HttpStatusCode.OK)
        }

        get("/state/{id}/stopped") {
            val id = call.parameters["id"]
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            Log.d("Provider", "$id has stopped")
            Scheduler.setInfo(id, null)
            Scheduler.setIntermediate(id, null)
            call.respond(HttpStatusCode.OK)
        }
    }
}