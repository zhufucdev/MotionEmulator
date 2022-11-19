package com.zhufucdev.motion_emulator.hook

import android.net.Uri
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.zhufucdev.motion_emulator.apps.isHooked
import com.zhufucdev.motion_emulator.hook_frontend.AUTHORITY

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        loadApp {
            if (isHooked()) {
                loadHooker(SensorHooker)
                loadHooker(LocationHooker)
                loadHooker(CellHooker)
                loadHooker(Scheduler.hook)

                loggerI("MotionEmulator", "Hooked $packageName")
            }
        }
    }
}

/**
 * This variable determines whether the sensor hooks work.
 *
 * Defaults to false. Use content provider to set.
 */
var hooking = false

const val EMULATION_START = 0x00
const val EMULATION_STOP = 0x01
