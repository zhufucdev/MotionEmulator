package com.zhufucdev.motion_emulator.provider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.zhufucdev.stub.BROADCAST_AUTHORITY

object Plugin {
    private const val pluginPackage = "com.zhufucdev.mock_location_plugin"

    fun isInstalled(context: Context): Boolean {
        try {
            context.packageManager.getApplicationInfo(pluginPackage, PackageManager.MATCH_ALL)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return true
    }

    private fun Context.broadcast(message: String) {
        sendBroadcast(Intent("$BROADCAST_AUTHORITY.$message").apply {
            component = ComponentName(pluginPackage, "$pluginPackage.PluginBroadcastReceiver")
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
    }

    fun wakeUp(context: Context) {
        context.broadcast("EMULATION_START")
    }

    fun notifySettingsChanged(context: Context) {
        context.broadcast("SETTINGS_CHANGED")
    }
}