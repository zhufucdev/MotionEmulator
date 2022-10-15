package com.zhufucdev.motion_emulator.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.zhufucdev.motion_emulator.hooking
import kotlin.math.abs

interface RecordCallback {
    fun summarize(): Motion
    fun onUpdate(l: (Moment) -> Unit)
    fun onUpdate(type: Int, l: (Moment) -> Unit)
}

const val VERTICAL_PERIOD = 0.01F

object Recorder {
    private lateinit var sensors: SensorManager

    private val callbacks = arrayListOf<RecordCallback>()

    fun init(context: Context) {
        sensors = context.getSystemService(SensorManager::class.java)
    }

    fun start(sensorsRequired: List<Int>): RecordCallback {
        val start = System.currentTimeMillis()
        val moments = arrayListOf<Moment>()
        var callbackListener: ((Moment) -> Unit)? = null
        val typedListeners = hashMapOf<Int, (Moment) -> Unit>()

        // turn off redirection for newly coming listeners
        // to register for the original system events
        hooking = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                fun typedFeedback(moment: Moment) {
                    typedListeners[event.sensor.type]?.invoke(moment)
                }

                val elapsed = (System.currentTimeMillis() - start) / 1000F
                if (moments.isNotEmpty()) {
                    for (i in moments.lastIndex downTo 0) {
                        if (abs(moments[i].elapsed - elapsed) < VERTICAL_PERIOD) {
                            moments[i].data[event.sensor.type] = event.values
                            typedFeedback(moments[i])
                            if (moments[i].data.size == sensorsRequired.size) {
                                callbackListener?.invoke(moments[i])
                            }
                            return
                        }
                    }
                }
                val newMoment = Moment(elapsed, mutableMapOf(event.sensor.type to event.values))
                moments.add(newMoment)
                typedFeedback(newMoment)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorsRequired.forEach {
            val sensor = sensors.getDefaultSensor(it)
            sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val result = object : RecordCallback {
            override fun summarize(): Motion {
                sensors.unregisterListener(listener)
                synchronized(Recorder) {
                    callbacks.remove(this)
                    hooking = callbacks.isEmpty()
                }

                return Motion(start, moments)
            }

            override fun onUpdate(l: (Moment) -> Unit) {
                callbackListener = l
            }

            override fun onUpdate(type: Int, l: (Moment) -> Unit) {
                typedListeners[type] = l
            }
        }

        synchronized(this) {
            callbacks.add(result)
        }

        return result
    }
}