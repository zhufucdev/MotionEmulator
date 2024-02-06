package com.zhufucdev.motion_emulator.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.me.stub.MotionMoment
import kotlin.math.abs

interface MotionCallback {
    fun summarize(): Motion
    fun onUpdate(l: (MotionMoment) -> Unit)
    fun onUpdate(type: Int, l: (MotionMoment) -> Unit)
}

const val VERTICAL_PERIOD = 0.05F

object MotionRecorder {
    private lateinit var sensors: SensorManager

    private val callbacks = arrayListOf<MotionCallback>()

    fun init(context: Context) {
        sensors = context.getSystemService(SensorManager::class.java)
    }

    fun start(sensorsRequired: List<Int>): MotionCallback {
        val start = System.currentTimeMillis()
        val moments = arrayListOf<MotionMoment>()
        var callbackListener: ((MotionMoment) -> Unit)? = null
        val typedListeners = hashMapOf<Int, (MotionMoment) -> Unit>()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                fun typedFeedback(moment: MotionMoment) {
                    typedListeners[event.sensor.type]?.invoke(moment)
                }

                val elapsed = (System.currentTimeMillis() - start) / 1000F
                if (moments.isNotEmpty()) {
                    val last = moments.last()
                    if (
                        abs(last.elapsed - elapsed) < VERTICAL_PERIOD
                        && !last.data.containsKey(event.sensor.type)
                    ) {
                        last.data[event.sensor.type] = event.values
                        typedFeedback(last)
                        if (last.data.size == sensorsRequired.size) {
                            callbackListener?.invoke(last)
                        }
                        return
                    }
                }

                val newMoment = MotionMoment(elapsed, mutableMapOf(event.sensor.type to event.values.clone()))
                moments.add(newMoment)
                typedFeedback(newMoment)
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

                return Motion(NanoIdUtils.randomNanoId(), moments, sensorsRequired)
            }

            override fun onUpdate(l: (MotionMoment) -> Unit) {
                callbackListener = l
            }

            override fun onUpdate(type: Int, l: (MotionMoment) -> Unit) {
                typedListeners[type] = l
            }
        }

        synchronized(this) {
            callbacks.add(result)
        }

        return result
    }
}