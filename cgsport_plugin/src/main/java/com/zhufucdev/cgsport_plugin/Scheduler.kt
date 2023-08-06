package com.zhufucdev.cgsport_plugin

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.MapProjector
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.Trace
import com.zhufucdev.stub.at
import com.zhufucdev.stub.generateSaltedTrace
import com.zhufucdev.stub.toPoint
import com.zhufucdev.stub_plugin.AbstractScheduler
import com.zhufucdev.stub_plugin.ServerScope
import com.zhufucdev.stub_plugin.WsServer
import com.zhufucdev.stub_plugin.connect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class Scheduler : AbstractScheduler() {
    private val emulationId = NanoIdUtils.randomNanoId()
    private var hookedPackage: String? = null
    override val packageName: String
        get() = hookedPackage ?: throw IllegalStateException("Hook not loaded")

    val hook = object : YukiBaseHooker() {
        override fun onHook() {
            hookedPackage = packageName
            loadHooker(AlsHooker(this@Scheduler))
            init()
        }
    }

    private fun PackageParam.init() {
        val scope = CoroutineScope(Dispatchers.IO)
        val prefs = prefs(PREF_BRIDGE_NAME)
        val wsServer = WsServer(
            host = prefs.getString(PREF_KEY_SERVER, "localhost"),
            port = prefs.getInt(PREF_KEY_PORT, 20230),
            useTls = prefs.getBoolean(PREF_KEY_TLS, true)
        )

        scope.launch {
            while (true) {
                wsServer.connect(emulationId) {
                    loggerD("Scheduler", "Ws server connected")
                    if (emulation.isPresent)
                        startEmulation(emulation.get())
                }
                delay(1.seconds)
            }
        }
    }

    var currentPoint = Point.zero
        private set

    override suspend fun ServerScope.startTraceEmulation(trace: Trace) {
        val salted = trace.generateSaltedTrace()
        var cache = salted.at(0f, MapProjector)
        while (loopProgress <= 1) {
            cache = salted.at(loopProgress, MapProjector, cache)
            currentPoint = cache.point.toPoint(trace.coordinateSystem)

            sendProgress(Intermediate(currentPoint, loopElapsed / 1000.0, loopProgress))
            delay(1.seconds)
        }
    }

    private val listeners = mutableListOf<(Point) -> Unit>()
    fun addLocationListener(listener: (Point) -> Unit): LocationListenerCallback {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }

        return object : LocationListenerCallback {
            override fun cancel() {
                listeners.remove(listener)
            }

            override fun resume() {
                if (!listeners.contains(listener)) {
                    listeners.add(listener)
                }
            }
        }
    }

    interface LocationListenerCallback {
        fun cancel()
        fun resume()
    }
}