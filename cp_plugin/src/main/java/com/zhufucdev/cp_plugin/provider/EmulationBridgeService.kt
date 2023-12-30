package com.zhufucdev.cp_plugin.provider

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zhufucdev.cp_plugin.R
import com.zhufucdev.me.plugin.MePlugin
import com.zhufucdev.me.plugin.WsServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class EmulationBridgeService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var server: WsServer
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        server = MePlugin.queryServer(this)
        job = scope.launch {
            while (true) {
                server.bridge()
                delay(2.seconds)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return Binder()
    }

    private fun requireForeground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                ChannelId,
                getString(R.string.title_bridge_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.text_bridge_channel) })

        val notification = NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_power_plug)
            .setContentTitle(getString(R.string.title_bridge_channel))
            .build()
        startForeground(1, notification)
    }

    companion object {
        const val ChannelId = "bridge_activity"
    }
}