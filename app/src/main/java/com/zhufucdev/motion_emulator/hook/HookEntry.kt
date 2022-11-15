package com.zhufucdev.motion_emulator.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        loadApp("com.totoro.school") {
            loadHooker(SensorHooker)
            loadHooker(LocationHooker)
            loadHooker(CellHooker)
            loadHooker(Scheduler.hook)
        }
    }

    init {
        loggerI(msg = "Greetings from MotionEmulator")
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