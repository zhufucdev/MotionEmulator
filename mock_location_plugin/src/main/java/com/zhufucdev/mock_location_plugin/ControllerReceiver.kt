package com.zhufucdev.mock_location_plugin

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import androidx.core.content.getSystemService
import com.zhufucdev.me.plugin.PluginBroadcastReceiver

class ControllerReceiver : PluginBroadcastReceiver() {
    override fun onEmulationStart(context: Context) {
        val js = context.getSystemService<JobScheduler>() ?: return

        val job = js.allPendingJobs.firstOrNull { it.id == 0 }
            ?: JobInfo.Builder(0, ComponentName(context, EmulationService::class.java)).build()
        js.schedule(job)
    }

    override fun onEmulationStop(context: Context) {
        if (MockLocationProvider.isWorking)
            MockLocationProvider.stop()
    }
}