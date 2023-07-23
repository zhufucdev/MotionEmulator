package com.zhufucdev.motion_emulator.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.zhufucdev.stub.BROADCAST_AUTHORITY

class Plugin(val packageName: String, val name: String, val description: String) {

    private fun Context.broadcast(message: String) {
        sendBroadcast(Intent("$BROADCAST_AUTHORITY.$message").apply {
            component = ComponentName(packageName, "$packageName.PluginBroadcastReceiver")
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
    }

    fun notifyStart(context: Context) {
        context.broadcast("EMULATION_START")
    }

    fun notifyStop(context: Context) {
        context.broadcast("EMULATION_STOP")
    }

    fun notifySettingsChanged(context: Context) {
        context.broadcast("SETTINGS_CHANGED")
    }
}