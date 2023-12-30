package com.zhufucdev.mock_location_plugin

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.mock_location_plugin.ui.MainActivity
import com.zhufucdev.mock_location_plugin.ui.TestFragment
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.me.stub.MapProjector
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.me.stub.android
import com.zhufucdev.me.stub.at
import com.zhufucdev.me.stub.estimateSpeed
import com.zhufucdev.me.stub.generateSaltedTrace
import com.zhufucdev.me.stub.toPoint
import com.zhufucdev.me.plugin.AbstractScheduler
import com.zhufucdev.me.plugin.WsServer
import com.zhufucdev.me.plugin.ServerScope
import com.zhufucdev.me.plugin.connect
import kotlinx.coroutines.delay

/**
 * A helper class that _implements_ the Mock Location api in the
 * developer options.
 *
 * I didn't read the documentation.
 * What I did was coping
 * [FakeTraveler](https://github.com/mcastillof/FakeTraveler/blob/master/app/src/main/java/cl/coders/faketraveler/MockLocationProvider.java)
 */
object MockLocationProvider : AbstractScheduler() {
    override val packageName: String
        get() = BuildConfig.APPLICATION_ID

    private val TARGET_PROVIDERS =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private val emulationId = NanoIdUtils.randomNanoId()

    private lateinit var locationManager: LocationManager
    private lateinit var server: WsServer
    fun init(context: Context, server: WsServer) {
        locationManager = context.getSystemService(LocationManager::class.java)
        val (powerUsage, accuracy) = if (Build.VERSION.SDK_INT >= 30) 1 to 2 else 0 to 5

        try {
            TARGET_PROVIDERS.forEach {
                // this is a perfect provider
                locationManager.addTestProvider(
                    it,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    true,
                    powerUsage,
                    accuracy
                )
                locationManager.setTestProviderEnabled(it, true)
            }
        } catch (_: SecurityException) {
            notifyNotAvailable(context)
            return
        }
        this.server = server
    }

    suspend fun emulate() {
        server.connect(emulationId) {
            Availability.notifyConnected(true)
            if (emulation.isPresent)
                startEmulation(emulation.get())
        }
    }

    var isEmulating = false
        private set

    override fun onEmulationStarted(emulation: Emulation) {
        isEmulating = true
    }

    override suspend fun ServerScope.startTraceEmulation(trace: Trace) {
        val salted = trace.generateSaltedTrace()
        var traceInterp = salted.at(0F, MapProjector)

        while (loopProgress <= 1F && isWorking) {
            val interp = salted.at(loopProgress, MapProjector, traceInterp)
            traceInterp = interp

            val current = interp.point.toPoint(trace.coordinateSystem)
            try {
                current.push()
            } catch (_: SecurityException) {
                stop()
                return
            }
            sendProgress(Intermediate(current, loopElapsed / 1000.0, loopProgress))
            delay(1000)
        }
    }

    private var lastLocation = Point(0.0, 0.0) to System.currentTimeMillis()
    private fun Point.push() {
        lastLocation = lastLocation.first.toPoint(coordinateSystem) to lastLocation.second
        val speed = estimateSpeed(this to System.currentTimeMillis(), lastLocation).toFloat()
        TARGET_PROVIDERS.forEach {
            locationManager.setTestProviderLocation(
                it, android(provider = it, speed = speed)
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

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_auto_fix_off)
                .setContentTitle(context.getString(R.string.title_not_available))
                .setContentText(context.getString(R.string.text_not_available))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        with(NotificationManagerCompat.from(context)) {
            notify(0, notification)
        }
    }

    fun stop() {
        isEmulating = false
        TARGET_PROVIDERS.forEach {
            locationManager.removeTestProvider(it)
        }
    }
}