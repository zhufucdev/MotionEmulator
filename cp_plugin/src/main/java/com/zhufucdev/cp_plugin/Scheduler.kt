package com.zhufucdev.cp_plugin

import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.Method
import com.zhufucdev.xposed.XposedScheduler
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
                GlobalScope.launch {
                    while (true) {
                        ContentProviderServer(PROVIDER_AUTHORITY, contentResolver).connect(id) {
                            if (emulation.isPresent)
                                startEmulation(emulation.get())
                        }
                        delay(2.seconds)
                    }
                }
            }
        }
    }

    override fun PackageParam.getHookingMethod(): Method =
        Method.entries[prefs(SHARED_PREFS_NAME).getInt("method")]
}