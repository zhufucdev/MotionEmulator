package com.zhufucdev.motion_emulator

import android.app.Application
import com.google.android.material.color.DynamicColors

class MeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}