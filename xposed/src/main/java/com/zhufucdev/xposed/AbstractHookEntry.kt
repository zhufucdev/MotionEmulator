package com.zhufucdev.xposed

import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

abstract class AbstractHookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        isDebug = BuildConfig.DEBUG
        debugLog { tag = "MotionEmulator" }
    }

    override fun onHook() = YukiHookAPI.encase {
        loadApp {
            loadApp()
        }
    }

    abstract fun PackageParam.loadApp()
}

