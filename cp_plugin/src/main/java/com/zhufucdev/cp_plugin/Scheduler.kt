package com.zhufucdev.cp_plugin

import com.highcapable.yukihookapi.hook.log.loggerW
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.me.stub.Method
import com.zhufucdev.me.xposed.XposedScheduler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class Scheduler : XposedScheduler() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun PackageParam.initialize() {
        onAppLifecycle {
            onCreate {
                val server = ContentProviderServer(PROVIDER_AUTHORITY, contentResolver)
                GlobalScope.launch {
                    connectServer(server)
                }
            }
        }
    }

    private suspend fun connectServer(server: ContentProviderServer) {
        var warned = false
        while (true) {
            val connection = server.connect(id) {
                if (emulation.isPresent)
                    startEmulation(emulation.get())
            }

            if (!warned && !connection.successful) {
                loggerW("Scheduler", "Failed to establish connection to content provider server")
                warned = true
            }

            delay(2.seconds)
        }
    }

    override fun PackageParam.getHookingMethod(): Method =
        Method.entries[prefs(SHARED_PREFS_NAME).getInt("method")]
}