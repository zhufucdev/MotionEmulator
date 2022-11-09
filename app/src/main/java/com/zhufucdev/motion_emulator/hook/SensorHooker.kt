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
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.zhufucdev.motion_emulator.data.MotionMoment

object SensorHooker : YukiBaseHooker() {
    private val listeners = arrayListOf<Pair<Int, SensorEventListener>>()
    private lateinit var sensorManager: SensorManager

    override fun onHook() {
        loadHooker(Scheduler.hook)

        classOf<SensorManager>().hook {
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, classOf<Handler>())
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, IntType)
            hookRegisterMethod(
                classOf<SensorEventListener>(), classOf<Sensor>(),
                IntType, IntType, classOf<Handler>()
            )
        }

        onAppLifecycle {
            attachBaseContext { baseContext, _ ->
                sensorManager = baseContext.getSystemService(SensorManager::class.java)
            }
        }
    }

    fun raise(moment: MotionMoment, typeFilter: Array<Int> = emptyArray()) {
        val eventConstructor =
            SensorEvent::class.constructors.firstOrNull { it.parameters.size == 4 }
                ?: error("sensor event constructor not available")
        val elapsed = SystemClock.elapsedRealtimeNanos()
        moment.data.forEach { (t, v) ->
            if (!typeFilter.contains(t)) {
                return@forEach
            }
            val sensor = sensorManager.getDefaultSensor(t)
            val event = eventConstructor.call(sensor, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, elapsed, v)
            listeners.forEach { (lt, l) ->
                if (lt == t) {
                    l.onSensorChanged(event)
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
                if (!hooking)
                    return@replaceAny callOriginal()
                redirectToFakeHandler()
            }
        }
    }

    private fun HookParam.redirectToFakeHandler(): Boolean {
        val type = args(1).cast<Sensor>()?.type ?: return false
        val listener = args(0).cast<SensorEventListener>() ?: return false
        listeners.add(type to listener)
        return true
    }
}