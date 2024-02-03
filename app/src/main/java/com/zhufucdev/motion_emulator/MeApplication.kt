package com.zhufucdev.motion_emulator

import android.app.Application
import com.zhufucdev.motion_emulator.plugin.Plugins

class MeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Plugins.init(this)
    }
}