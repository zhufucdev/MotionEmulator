package com.zhufucdev.motion_emulator.hook

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.os.SystemClock
import com.zhufucdev.motion_emulator.data.Moment

/**
 * A handler for sensor event listener
 * that raises events as will
 */
object SensorHandler {
    private val listeners = mutableSetOf<Pair<Int, ((SensorEvent) -> Unit)>>()
    private lateinit var sm: SensorManager

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
            listeners.forEach {
                if (it.first == t)
                    it.second.invoke(event)
            }
        }
    }

    fun addRedirectedListener(type: Int, l: (SensorEvent) -> Unit) {
        listeners.add(type to l)
    }
}