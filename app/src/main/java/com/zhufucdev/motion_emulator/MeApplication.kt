package com.zhufucdev.motion_emulator

import com.google.android.material.color.DynamicColors
import com.highcapable.yukihookapi.hook.xposed.application.ModuleApplication

class MeApplication : ModuleApplication() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}