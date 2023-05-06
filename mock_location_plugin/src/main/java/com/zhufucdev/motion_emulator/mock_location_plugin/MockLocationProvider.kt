package com.zhufucdev.motion_emulator.mock_location_plugin

import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.data.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import kotlin.time.Duration.Companion.seconds

/**
 * A helper class that _implements_ the Mock Location api in the
 * developer options.
 *
 * I didn't read the documentation.
 * What I did was coping
 * [FakeTraveler](https://github.com/mcastillof/FakeTraveler/blob/master/app/src/main/java/cl/coders/faketraveler/MockLocationProvider.java)
 */
object MockLocationProvider {
    private val TARGET_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private val emulationId = NanoIdUtils.randomNanoId()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val jobs = arrayListOf<Job>()
    private val ktor = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }

        engine {
            // disable certificate verification
            sslManager = { connection ->
                connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                    init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                }.socketFactory
            }
        }
    }

    private lateinit var locationManager: LocationManager
    fun init(context: Context, port: Int, tls: Boolean) {
        locationManager = context.getSystemService(LocationManager::class.java)
        val (powerUsage, accuracy) = if (Build.VERSION.SDK_INT >= 30) 1 to 2 else 0 to 5
        val providerAddr = (if (tls) "https://" else "http://") + "127.0.0.1:$port"

        TARGET_PROVIDERS.forEach {
            // this is a perfect provider
            locationManager.addTestProvider(it, false, false, false, false, false, true, true, powerUsage, accuracy)
        }

        jobs.add(
            scope.launch {
                val current =
                    ktor.get("$providerAddr/current").takeIf { it.status == HttpStatusCode.OK }?.body<Emulation>()
                if (current != null) {
                    startEmulation(current, providerAddr)
                }
            }
        )

        jobs.add(
            scope.launch {
                while (true) {
                    eventLoop(providerAddr)
                    jobs.removeIf { !it.isActive }
                    delay(1.seconds)
                }
            }
        )
    }

    var isEmulating = false
        private set

    private suspend fun startEmulation(emulation: Emulation, providerAddr: String) {
        if (isEmulating) return
        isEmulating = true

        val trace = emulation.trace
        val length = trace.length(MapProjector)
        val duration = length / emulation.velocity

        notifyStatus(EmulationInfo(duration, length, BuildConfig.APPLICATION_ID), providerAddr)

        val salted = trace.generateSaltedPoints(MapProjector)
        var traceInterp = salted.at(0F, MapProjector)
        var loopStart: Long
        var currentLoop = 0
        while (isEmulating && currentLoop < emulation.repeat) {
            var progress = 0F
            loopStart = System.currentTimeMillis()

            while (progress <= 1F) {
                val interp = salted.at(progress, MapProjector, traceInterp)
                traceInterp = interp
                val current = interp.point.toPoint(trace.coordinateSystem)
                current.push()
                delay(1000)

                progress = ((System.currentTimeMillis() - loopStart) / duration).toFloat()
            }
            currentLoop++
        }

        notifyStatus(null, providerAddr)
    }

    private suspend fun eventLoop(providerAddr: String) {
        try {
            while (true) {
                val next =
                    ktor.get("$providerAddr/next")
                        .takeIf { it.status == HttpStatusCode.OK }
                        ?.body<Emulation>()
                if (next != null) {
                    startEmulation(next, providerAddr)
                } else {
                    isEmulating = false
                }
                jobs.removeIf { !it.isActive }
            }
        } catch (_: Exception) {
            // ignored
        }
    }

    private suspend fun notifyStatus(status: EmulationInfo?, providerAddr: String) {
        if (status != null) {
            ktor.post("$providerAddr/state/${emulationId}/running") {
                contentType(ContentType.Application.Json)
                setBody(status)
            }
        } else {
            ktor.get("$providerAddr/state/${emulationId}/stopped")
        }
    }

    private var lastLocation = Point(0.0, 0.0) to System.currentTimeMillis()
    private fun Point.push() {
        val speed = estimateSpeed(this to System.currentTimeMillis(), lastLocation, MapProjector).toFloat()
        TARGET_PROVIDERS.forEach {
            locationManager.setTestProviderLocation(
                it,
                android(
                    provider = LocationManager.GPS_PROVIDER,
                    speed = speed,
                    mapProjector = MapProjector
                )
            )
        }
        lastLocation = this to System.currentTimeMillis()
    }

    fun stop() {
        isEmulating = false
        scope.cancel()
        TARGET_PROVIDERS.forEach {
            locationManager.removeTestProvider(it)
        }
    }

    suspend fun wait() {
        jobs.toList().joinAll() // make a copy
    }
}