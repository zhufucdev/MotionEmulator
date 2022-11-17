package com.zhufucdev.motion_emulator.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.zhufucdev.motion_emulator.apps.AppMetas
import com.zhufucdev.motion_emulator.apps.hooked

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = YukiHookAPI.encase {
        onAppLifecycle {
            attachBaseContext { baseContext, _ ->
                AppMetas.require(baseContext)
                AppMetas.list().forEach {
                    if (!it.hooked) return@forEach

                    loadApp(it.packageName) {
                        loadHooker(SensorHooker)
                        loadHooker(LocationHooker)
                        loadHooker(CellHooker)
                        loadHooker(Scheduler.hook)
                    }
                    loggerI("MotionEmulator", "Hooked ${it.packageName}")
                }
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