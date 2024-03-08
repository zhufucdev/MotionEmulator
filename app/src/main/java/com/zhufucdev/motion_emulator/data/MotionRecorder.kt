package com.zhufucdev.motion_emulator.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.me.stub.SensorMoment

interface MotionCallback {
    fun summarize(): Motion
    fun onUpdate(l: (SensorMoment) -> Unit)
    fun onUpdate(type: Int, l: (SensorMoment) -> Unit)
}

object MotionRecorder {
    private lateinit var sensors: SensorManager

    private val callbacks = arrayListOf<MotionCallback>()

    fun init(context: Context) {
        sensors = context.getSystemService(SensorManager::class.java)
    }

    fun start(sensorsRequired: List<Int>): MotionCallback {
        val start = System.currentTimeMillis()
        val timelines = sensorsRequired.associateWith { arrayListOf<SensorMoment>() }
        var callbackListener: ((SensorMoment) -> Unit)? = null
        val typedListeners = hashMapOf<Int, (SensorMoment) -> Unit>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                fun invokeTypedListener(moment: SensorMoment) {
                    typedListeners[event.sensor.type]?.invoke(moment)
                }

                val elapsed = (System.currentTimeMillis() - start) / 1000F
                val timeline = timelines[event.sensor.type]!!
                val moment = SensorMoment(elapsed, event.values)

                timeline.add(moment)
                invokeTypedListener(moment)
                callbackListener?.invoke(moment)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorsRequired.forEach {
            val sensor = sensors.getDefaultSensor(it)
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        val result = object : MotionCallback {
            override fun summarize(): Motion {
                sensors.unregisterListener(listener)
                synchronized(MotionRecorder) {
                    callbacks.remove(this)
                }

                return Motion(NanoIdUtils.randomNanoId(), timelines)
            }

            override fun onUpdate(l: (SensorMoment) -> Unit) {
                callbackListener = l
            }

            override fun onUpdate(type: Int, l: (SensorMoment) -> Unit) {
                typedListeners[type] = l
            }
        }

        synchronized(this) {
            callbacks.add(result)
        }

        return result
    }
}