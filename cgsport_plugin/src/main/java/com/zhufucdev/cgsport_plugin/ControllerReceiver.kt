package com.zhufucdev.cgsport_plugin

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.zhufucdev.stub_plugin.MePlugin
import com.zhufucdev.stub_plugin.PluginBroadcastReceiver

class ControllerReceiver : PluginBroadcastReceiver() {
    override fun onSettingsChanged(context: Context) {
        val server = MePlugin.queryServer(context)
        context.prefs(PREF_BRIDGE_NAME).edit {
            putString(PREF_KEY_SERVER, server.host)
            putInt(PREF_KEY_PORT, server.port)
            putBoolean(PREF_KEY_TLS, server.useTls)
        }
    }
}