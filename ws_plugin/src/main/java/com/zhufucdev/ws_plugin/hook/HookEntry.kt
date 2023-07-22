package com.zhufucdev.ws_plugin.hook

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.xposed.AbstractHookEntry

@InjectYukiHookWithXposed
class HookEntry : AbstractHookEntry() {
    override fun PackageParam.loadApp() {
        loadHooker(Scheduler.hook)
    }
}