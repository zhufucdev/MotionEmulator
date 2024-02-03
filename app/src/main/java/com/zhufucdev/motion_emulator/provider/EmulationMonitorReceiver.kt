package com.zhufucdev.motion_emulator.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class EmulationMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            INTENT_ACTION_DETERMINE -> {
                Scheduler.emulation = null
                WorkManager.getInstance(context)
                    .cancelUniqueWork(WORK_NAME_MONITOR)
            }
        }
    }
}

const val INTENT_ACTION_DETERMINE = "com.zhufucdev.motion_emulator.ACTION_DETERMINE"
const val WORK_NAME_MONITOR = "com.zhufucdev.motion_emulator.monitor"