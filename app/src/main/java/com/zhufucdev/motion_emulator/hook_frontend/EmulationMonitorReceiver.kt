package com.zhufucdev.motion_emulator.hook_frontend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.zhufucdev.motion_emulator.ui.emulate.WORK_NAME_MONITOR

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