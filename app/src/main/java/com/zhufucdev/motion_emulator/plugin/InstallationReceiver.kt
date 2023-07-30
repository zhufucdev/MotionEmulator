package com.zhufucdev.motion_emulator.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class InstallationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Plugins.initialized) return
        if (intent.action == Intent.ACTION_PACKAGE_ADDED ||
            intent.action == Intent.ACTION_PACKAGE_CHANGED ||
            intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED
        ) {
            Plugins.loadAvailablePlugins(context)
        }
    }
}