package com.zhufucdev.motion_emulator.mock_location_plugin

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.zhufucdev.data.BROADCAST_AUTHORITY

class EmulationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("EmulationBroadcastReceiver", "${intent.action} received")
        val js = context.getSystemService<JobScheduler>() ?: return

        val job = js.getPendingJob(0)
            ?: JobInfo.Builder(0, ComponentName(context, EmulationService::class.java)).build()

        when (intent.action) {
            "$BROADCAST_AUTHORITY.EMULATION_START" -> {
                val (port, tls) = intent.extras?.let { it.getInt("port") to it.getBoolean("tls") } ?: return
                MockLocationProvider.init(context, port, tls)
                js.schedule(job)
            }

            "$BROADCAST_AUTHORITY.EMULATION_STOP" -> {
                if (MockLocationProvider.isEmulating)
                    MockLocationProvider.stop()
            }
        }
    }
}