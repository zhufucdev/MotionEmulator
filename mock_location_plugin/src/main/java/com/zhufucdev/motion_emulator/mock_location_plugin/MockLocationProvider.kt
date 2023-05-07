package com.zhufucdev.motion_emulator.mock_location_plugin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.data.*
import com.zhufucdev.motion_emulator.mock_location_plugin.ui.TestFragment
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
    private const val TAG = "MockLocationProvider"

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

            connectTimeout = 0
            socketTimeout = 0
        }
    }

    private lateinit var locationManager: LocationManager
    fun init(context: Context, port: Int, tls: Boolean) {
        locationManager = context.getSystemService(LocationManager::class.java)
        val (powerUsage, accuracy) = if (Build.VERSION.SDK_INT >= 30) 1 to 2 else 0 to 5
        val providerAddr = (if (tls) "https://" else "http://") + "127.0.0.1:$port"

        try {
            TARGET_PROVIDERS.forEach {
                // this is a perfect provider
                locationManager.addTestProvider(it, false, false, false, false, false, true, true, powerUsage, accuracy)
                locationManager.setTestProviderEnabled(it, true)
            }
        } catch (_: SecurityException) {
            notifyNotAvailable(context)
            return
        }

        jobs.add(scope.launch {
            val current = try {
                val response = ktor.get("$providerAddr/current")
                Availability.notifyConnected(true)
                response.takeIf { it.status == HttpStatusCode.OK }?.body<Emulation>()
            } catch (_: Exception) {
                Availability.notifyConnected(false)
                null
            }
            if (current != null) {
                startEmulation(current, providerAddr)
            }
        })

        jobs.add(scope.launch {
            while (true) {
                eventLoop(providerAddr)
                jobs.removeIf { !it.isActive }
                delay(1.seconds)
            }
        })
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

        val salted = trace.generateSaltedTrace(MapProjector)
        var traceInterp = salted.at(0F, MapProjector)
        var loopStart: Long
        var currentLoop = 0
        while (isEmulating && currentLoop < emulation.repeat) {
            var progress = 0F
            var elapsed = 0.0
            loopStart = System.currentTimeMillis()

            while (progress <= 1F && isEmulating) {
                val interp = salted.at(progress, MapProjector, traceInterp)
                traceInterp = interp

                val current = interp.point.toPoint(trace.coordinateSystem)
                try {
                    current.push()
                } catch (_: SecurityException) {
                    stop()
                    break
                }
                notifyProgress(Intermediate(current, elapsed, progress), providerAddr)
                delay(1000)

                elapsed = (System.currentTimeMillis() - loopStart) / 1000.0
                progress = (elapsed / duration).toFloat()
            }
            currentLoop++
        }

        notifyStatus(null, providerAddr)
    }

    private suspend fun eventLoop(providerAddr: String) {
        Log.i(TAG, "event loop started")
        try {
            while (true) {
                val next = ktor.get("$providerAddr/next/$emulationId").takeIf { it.status == HttpStatusCode.OK }
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
            Log.i(TAG, "emulation ($status) running")
            ktor.post("$providerAddr/state/${emulationId}/running") {
                contentType(ContentType.Application.Json)
                setBody(status)
            }
        } else {
            Log.i(TAG, "emulation stopped")
            ktor.get("$providerAddr/state/${emulationId}/stopped")
        }
    }

    private suspend fun notifyProgress(intermediate: Intermediate, providerAddr: String) {
        val res = ktor.post("$providerAddr/intermediate/${emulationId}") {
            contentType(ContentType.Application.Json)
            setBody(intermediate)
        }
        if (!res.status.isSuccess()) {
            Log.w(TAG, "while updating progress, server responded with ${res.status.value}")
            Availability.notifyConnected(false)
        } else {
            Availability.notifyConnected(true)
        }
    }

    private var lastLocation = Point(0.0, 0.0) to System.currentTimeMillis()
    private fun Point.push() {
        lastLocation = lastLocation.first.toPoint(coordinateSystem) to lastLocation.second
        val speed = estimateSpeed(this to System.currentTimeMillis(), lastLocation, MapProjector).toFloat()
        TARGET_PROVIDERS.forEach {
            locationManager.setTestProviderLocation(
                it, android(
                    provider = it, speed = speed, mapProjector = MapProjector
                )
            )
        }
        lastLocation = this to System.currentTimeMillis()
    }

    private fun notifyNotAvailable(context: Context) {
        if (TestFragment.inForeground) {
            // don't be annoying
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            && ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_baseline_auto_fix_off)
                .setContentTitle(context.getString(R.string.title_not_available))
                .setContentText(context.getString(R.string.text_not_available))
                .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        with(NotificationManagerCompat.from(context)) {
            notify(0, notification)
        }
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