package com.zhufucdev.motion_emulator.hook

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.zhufucdev.motion_emulator.hooking

object SensorHooker : YukiBaseHooker() {
    private fun YukiMemberHookCreator.hookRegisterMethod(vararg paramType: Any) {
        injectMember {
            method {
                name = "registerListener"
                param(*paramType)
                returnType = BooleanType
            }
            beforeHook {
                hookEventListener(args(0))
            }
        }
    }

    override fun onHook() {
        classOf<SensorManager>().hook {
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, classOf<Handler>())
            hookRegisterMethod(classOf<SensorEventListener>(), classOf<Sensor>(), IntType, IntType)
            hookRegisterMethod(
                classOf<SensorEventListener>(), classOf<Sensor>(),
                IntType, IntType, classOf<Handler>()
            )
        }
    }

    private fun hookEventListener(instance: Any) {
        instance::class.java.hook {
            injectMember {
                method {
                    name ="onSensorChanged"
                    param(classOf<SensorEvent>())
                    returnType = UnitType
                }
                beforeHook {
                    if (!hooking) return@beforeHook
                    val event = args(0).any() as SensorEvent

                }
            }
        }
    }
}