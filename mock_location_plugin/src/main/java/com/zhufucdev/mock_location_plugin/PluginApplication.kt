package com.zhufucdev.mock_location_plugin

import android.app.Application
import com.google.android.material.color.DynamicColors

class PluginApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}