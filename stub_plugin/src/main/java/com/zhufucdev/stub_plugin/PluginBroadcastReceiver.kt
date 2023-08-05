package com.zhufucdev.stub_plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhufucdev.stub.BROADCAST_AUTHORITY

abstract class PluginBroadcastReceiver : BroadcastReceiver() {
    open fun onEmulationStart(context: Context) {}
    open fun onEmulationStop(context: Context) {}
    open fun onSettingsChanged(context: Context) {}

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "$BROADCAST_AUTHORITY.EMULATION_START" -> {
                onEmulationStart(context)
            }

            "$BROADCAST_AUTHORITY.EMULATION_STOP" -> {
                onEmulationStop(context)
            }

            "$BROADCAST_AUTHORITY.SETTINGS_CHANGED" -> {
                onSettingsChanged(context)
            }
        }
    }
}