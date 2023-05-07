package com.zhufucdev.motion_emulator.provider

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.zhufucdev.data.BROADCAST_AUTHORITY
import com.zhufucdev.motion_emulator.lazySharedPreferences

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

    fun wakeUp(context: Context, port: Int, tls: Boolean) {
        if (context.lazySharedPreferences().value.getBoolean("use_test_provider", false)) {
            context.sendBroadcast(Intent("$BROADCAST_AUTHORITY.EMULATION_START").apply {
                component = ComponentName(pluginPackage, "$pluginPackage.EmulationBroadcastReceiver")
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                putExtra("port", port)
                putExtra("tls", tls)
            })
            Log.i("Schedular", "broadcast sent")
        }
    }
}