package com.zhufucdev.motion_emulator.hook_frontend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.hardware.Sensor
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.hooking
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class EmulationWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {


    override suspend fun doWork(): Result {
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        registerChannel()
        return createForegroundInfo(0F)
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val title = applicationContext.getString(R.string.title_emulation_ongoing)
        val cancelIntent =
            WorkManager.getInstance(applicationContext)
                .createCancelPendingIntent(id)

        val notification =
            NotificationCompat.Builder(
                applicationContext,
                CHANNEL_ID
            )
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(applicationContext.getString(R.string.text_emulation_ongoing))
                .setProgress(1000, (progress * 1000).roundToInt(), false)
                .setSmallIcon(R.drawable.ic_baseline_auto_fix_high_24)
                .addAction(
                    R.drawable.ic_baseline_stop_24, applicationContext.getString(R.string.action_stop), cancelIntent
                )
                .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun registerChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        with(applicationContext) {
            val name = getString(R.string.title_channel_emulation)
            val description = getString(R.string.text_channel_emulation)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_MIN)
                        .apply {
                            setDescription(description)
                        }
                )
        }
    }

    companion object {
        const val NOTIFICATION_ID = 0
        const val CHANNEL_ID = "emulation_activity"
    }
}