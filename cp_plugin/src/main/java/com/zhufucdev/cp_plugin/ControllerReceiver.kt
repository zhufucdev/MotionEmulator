package com.zhufucdev.cp_plugin

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.highcapable.yukihookapi.hook.factory.prefs
import com.zhufucdev.cp_plugin.provider.EmulationBridgeService
import com.zhufucdev.stub_plugin.MePlugin
import com.zhufucdev.stub_plugin.PluginBroadcastReceiver

class ControllerReceiver : PluginBroadcastReceiver() {
    override fun onEmulationStart(context: Context) {
        context.prefs(SHARED_PREFS_NAME).edit {
            putInt("method", MePlugin.queryMethod(context).ordinal)
        }

//        Intent(context, EmulationBridgeService::class.java).apply {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                context.startForegroundService(this)
//            } else {
//                context.startService(this)
//            }
//        }
    }

    override fun onEmulationStop(context: Context) {
//        Intent(context, EmulationBridgeService::class.java).apply {
//            context.stopService(this)
//        }
    }
}