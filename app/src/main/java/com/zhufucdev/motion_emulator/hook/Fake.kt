package com.zhufucdev.motion_emulator.hook

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.highcapable.yukihookapi.hook.log.loggerI
import com.zhufucdev.motion_emulator.data.Moment
import com.zhufucdev.motion_emulator.data.Point
import kotlin.random.Random

/**
 * A handler for sensor and location
 * event listener that raises events as will
 */
object Fake {
    private val sListeners = mutableSetOf<Pair<Int, SensorEventListener>>()
    private val lListeners = mutableSetOf<(Point) -> Unit>()
    private lateinit var sm: SensorManager

    private var mLocation: Point? = null
    var location: Point
        get() {
            return mLocation ?: Point(39.989410, 116.480881)
        }
        set(value) {
            if (mLocation == value) return
            mLocation = value
            triggerLocationChange(value)
        }

    fun init(context: Context) {
        sm = context.getSystemService(SensorManager::class.java)
    }

    fun stimulate(moment: Moment) {
        val eventConstructor =
            SensorEvent::class.constructors.firstOrNull { it.parameters.firstOrNull()?.type == Sensor::class }
                ?: error("sensor event constructor not available")
        val elapsed = SystemClock.elapsedRealtimeNanos()
        moment.data.forEach { (t, v) ->
            val sensor = sm.getDefaultSensor(t)
            val event = eventConstructor.call(sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, elapsed, v)
            sListeners.forEach {
                if (it.first == t)
                    it.second.onSensorChanged(event)
            }
        }
    }

    private fun triggerLocationChange(location: Point) {
        lListeners.forEach {
            it.invoke(location)
        }
    }

    fun addSensorListener(type: Int, l: SensorEventListener) {
        sListeners.add(type to l)
    }

    fun addLocationListener(l: (Point) -> Unit) {
        lListeners.add(l)
        l.invoke(location)
    }
}

fun Point.android(provider: String = LocationManager.GPS_PROVIDER): Location {
    val result = Location(provider).apply {
        // fake some data
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            elapsedRealtimeUncertaintyNanos = 5000.0 + (Random.nextDouble() - 0.5) * 1000
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = Random.nextFloat() * 10
        }
        accuracy = Random.nextFloat() * 10
    }

    result.latitude = latitude
    result.longitude = longitude

    return result
}