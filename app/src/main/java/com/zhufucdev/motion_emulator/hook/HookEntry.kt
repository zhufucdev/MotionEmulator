package com.zhufucdev.motion_emulator.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.zhufucdev.motion_emulator.BuildConfig
import com.zhufucdev.motion_emulator.data.isHooked

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
        debugLog { tag = "MotionEmulator" }
    }

    override fun onHook() = YukiHookAPI.encase {
        loadApp {
            if (isHooked()) {
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
