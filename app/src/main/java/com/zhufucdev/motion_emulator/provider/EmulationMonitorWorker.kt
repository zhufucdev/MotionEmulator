package com.zhufucdev.motion_emulator.provider

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.zhufucdev.motion_emulator.ui.EmulateActivity
import com.zhufucdev.motion_emulator.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

class EmulationMonitorWorker(appContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        while (true) {
            if (Scheduler.emulation == null) break

            val progress =
                if (Scheduler.intermediate.size == 1)
                    Scheduler.intermediate.values.first().progress
                else
                    -1F
            setForeground(createForegroundInfo(progress))
            delay(1.0.seconds)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(-1F)
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val title = applicationContext.getString(R.string.title_emulation_ongoing)
        val determineIntent =
            Intent(applicationContext, EmulationMonitorReceiver::class.java).apply {
                action = INTENT_ACTION_DETERMINE
            }
        val determinePendingIntent =
            PendingIntent.getBroadcast(applicationContext, 0, determineIntent, FLAG_IMMUTABLE)
        val contentIntend =
            Intent(applicationContext, EmulateActivity::class.java)
        val contentPendingIntent =
            PendingIntent.getActivity(applicationContext, 0, contentIntend, FLAG_IMMUTABLE)

        val notification =
            NotificationCompat.Builder(
                applicationContext,
                CHANNEL_ID
            )
                .setContentTitle(title)
                .setTicker(title)
                .setContentText(applicationContext.getString(R.string.text_emulation_ongoing))
                .setContentIntent(contentPendingIntent)
                .setProgress(1000, (progress * 1000).roundToInt(), progress < 0)
                .setSmallIcon(R.drawable.ic_baseline_auto_fix_high_24)
                .addAction(
                    R.drawable.ic_baseline_stop_24,
                    applicationContext.getString(R.string.action_determine),
                    determinePendingIntent
                )
                .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NOTIFICATION_ID = 0
        const val CHANNEL_ID = "emulation_activity"
    }
}