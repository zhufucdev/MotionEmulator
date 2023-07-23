package com.zhufucdev.motion_emulator

import android.app.Application
import com.google.android.material.color.DynamicColors
import com.zhufucdev.motion_emulator.plugin.Plugins

class MeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        Plugins.init(this)
    }
}