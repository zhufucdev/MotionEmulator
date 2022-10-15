package com.zhufucdev.motion_emulator

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.type.java.UnitType
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.zhufucdev.motion_emulator.data.Recorder
import com.zhufucdev.motion_emulator.hook.SensorHooker

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        loadSystem {
            loadHooker(SensorHooker)
        }
    }
}

/**
 * This variable determines whether the sensor hooks work.
 */
var hooking = true