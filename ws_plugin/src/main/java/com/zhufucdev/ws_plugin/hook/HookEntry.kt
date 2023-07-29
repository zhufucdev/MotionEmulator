package com.zhufucdev.ws_plugin.hook

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import com.zhufucdev.ws_plugin.BuildConfig

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
        debugLog { tag = "WS Plugin" }
    }

    override fun onHook() = YukiHookAPI.encase {
        loadApp(isExcludeSelf = true) {
            val scheduler = Scheduler()
            loadHooker(scheduler.hook)
        }
    }
}