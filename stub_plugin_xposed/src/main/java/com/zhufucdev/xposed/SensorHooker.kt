package com.zhufucdev.xposed

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.SystemClock
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.stub.MotionMoment
import com.zhufucdev.stub.Toggle
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class SensorHooker(private val scheduler: XposedScheduler) : YukiBaseHooker() {
    private val listeners = mutableSetOf<SensorListener>()

    var toggle = Toggle.PRESENT

    override fun onHook() {
        classOf<SensorManager>().hook {
            hookRegisterMethod(
                classOf<SensorEventListener>(),
                classOf<Sensor>(),
                IntType,
                classOf<Handler>()
            )
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType)
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, IntType)
            hookRegisterMethod(
                classOf<SensorEventListener>(), classOf<Sensor>(),
                IntType, IntType, classOf<Handler>()
            )
            hookUnregisterMethod(classOf<SensorEventListener>(), classOf<Sensor>())
            hookUnregisterMethod(classOf<SensorEventListener>())
        }
    }

    suspend fun raise(moment: MotionMoment) {
        val eventConstructor =
            SensorEvent::class.constructors.firstOrNull { it.parameters.size == 4 }
                ?: SensorEvent::class.constructors.firstOrNull { it.parameters.size == 1 }
                ?: error("SensorEvent constructor not available")
        val elapsed = SystemClock.elapsedRealtimeNanos()
        moment.data.forEach { (t, v) ->
            val sensor =
                appContext!!.getSystemService(SensorManager::class.java).getDefaultSensor(t)
            val values = scheduler.motion.data[t] ?: v
            val event = when (val pars = eventConstructor.parameters.size) {
                4 -> eventConstructor.call(
                    sensor,
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH,
                    elapsed,
                    values
                )

                1 -> eventConstructor.call(values.size).apply {
                    values.copyInto(this.values)
                    accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
                    timestamp = elapsed
                    this.sensor = sensor
                }

                else -> throw NotImplementedError("Constructor to SensorEvent with $pars parameters not implemented")
            }
            listeners.forEach { (lt, l, h) ->
                if (lt == t) {
                    supervisorScope {
                        async {
                            h?.post { l.onSensorChanged(event) } ?: l.onSensorChanged(event)
                        }
                    }.start()
                }
            }
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
                if (!scheduler.isWorking || toggle == Toggle.NONE)
                    return@replaceAny callOriginal()
                if (toggle == Toggle.BLOCK)
                    return@replaceAny false
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
                if (!scheduler.isWorking || toggle == Toggle.NONE) {
                    callOriginal()
                    return@replaceUnit
                }
                if (toggle == Toggle.BLOCK) {
                    return@replaceUnit
                }
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
        return true
    }
}

data class SensorListener(
    val type: Int,
    val listener: SensorEventListener,
    val handler: Handler? = null
)