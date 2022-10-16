package com.zhufucdev.motion_emulator.hook

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.highcapable.yukihookapi.hook.core.YukiMemberHookCreator
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.param.HookParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.zhufucdev.motion_emulator.hooking

object SensorHooker : YukiBaseHooker() {
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
        Fake.addSensorListener(type, listener)
        return true
    }
}