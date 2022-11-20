package com.zhufucdev.motion_emulator.hook

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.motion_emulator.data.MotionMoment
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

object SensorHooker : YukiBaseHooker() {
    private const val TAG = "SensorHook"

    private val listeners = mutableSetOf<SensorListener>()

    override fun onHook() {
        classOf<SensorManager>().hook {
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, classOf<Handler>())
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, IntType)
            hookRegisterMethod(
                classOf<SensorEventListener>(), classOf<Sensor>(),
                IntType, IntType, classOf<Handler>()
            )
            hookUnregisterMethod(classOf<SensorEventListener>())
            hookUnregisterMethod(classOf<SensorEventListener>(), classOf<Sensor>())
        }
    }

    suspend fun raise(moment: MotionMoment) {
        val eventConstructor =
            SensorEvent::class.constructors.firstOrNull { it.parameters.size == 4 }
                ?: error("sensor event constructor not available")
        val elapsed = SystemClock.elapsedRealtimeNanos()
        loggerD(TAG, "Raising with moment($moment)")
        moment.data.forEach { (t, v) ->
            val sensor = appContext!!.getSystemService(SensorManager::class.java).getDefaultSensor(t)
            val event = eventConstructor.call(sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, elapsed, v)
            listeners.forEach { (lt, l, h) ->
                if (lt == t) {
                    loggerD(TAG, "Sensor typed $lt invoked with data [${v.joinToString()}]")
                    supervisorScope {
                        async {
                            h?.post { l.onSensorChanged(event) } ?: l.onSensorChanged(event)
                        }
                    }.start()
                }
            }
            loggerD(TAG, "Sensor invoke ended")
        }
    }

    private fun YukiMemberHookCreator.hookRegisterMethod(vararg paramType: Any) {
        injectMember {
            method {
                name = "registerListener"
                param(*paramType)
                returnType = BooleanType
            }
            replaceAny {
                if (!hooking)
                    return@replaceAny callOriginal()
                redirectToFakeHandler()
            }
        }
    }

    private fun YukiMemberHookCreator.hookUnregisterMethod(vararg paramType: Any) {
        injectMember {
            method {
                name = "unregisterListener"
                param(*paramType)
                returnType = UnitType
            }

            replaceUnit {
                val listener = args(0).cast<SensorEventListener>()
                val sensor = args.takeIf { it.size > 1 }?.let { it[1] as? Sensor }
                if (sensor == null) {
                    listeners.removeAll { it.listener == listener }
                } else {
                    listeners.removeAll { it.listener == listener && it.type == sensor.type }
                }
            }
        }
    }

    private fun HookParam.redirectToFakeHandler(): Boolean {
        val type = args(1).cast<Sensor>()?.type ?: return false
        val listener = args(0).cast<SensorEventListener>() ?: return false
        val handler = args.lastOrNull() as? Handler
        listeners.add(SensorListener(type, listener, handler))
        loggerD(TAG, buildString {
            append("Registered type $type")
            if (handler != null)
                append(" with custom handler")
        })
        return true
    }
}

data class SensorListener(val type: Int, val listener: SensorEventListener, val handler: Handler? = null)