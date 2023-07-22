package com.zhufucdev.ws_plugin.hook

import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.xposed.AbstractHookEntry

class HookEntry : AbstractHookEntry() {
    override fun PackageParam.loadApp() {
        loadHooker(Scheduler.hook)
    }
}