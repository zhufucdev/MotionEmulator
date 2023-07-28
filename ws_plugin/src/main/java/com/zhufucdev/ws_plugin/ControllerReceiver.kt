package com.zhufucdev.ws_plugin

import android.content.Context
import com.highcapable.yukihookapi.hook.factory.prefs
import com.highcapable.yukihookapi.hook.log.loggerD
import com.zhufucdev.stub_plugin.MePlugin
import com.zhufucdev.stub_plugin.PluginBroadcastReceiver
import com.zhufucdev.xposed.PREFERENCE_NAME_BRIDGE

class ControllerReceiver : PluginBroadcastReceiver() {
    override fun onEmulationStart(context: Context) {
        // left to Scheduler
    }

    override fun onSettingsChanged(context: Context) {
        loggerD("Ws Plugin", "settings notified")
        context.prefs(PREFERENCE_NAME_BRIDGE).edit {
            val server = MePlugin.queryServer(context)
            val method = MePlugin.queryMethod(context)
            putBoolean("me_server_tls", server.useTls)
            putInt("me_server_port", server.port)
            putString("me_method", method.name)
        }
    }
}