package com.zhufucdev.motion_emulator

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.plugin.WsServer
import com.zhufucdev.me.plugin.connect
import com.zhufucdev.me.stub.EmptyBox
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.Intermediate
import com.zhufucdev.me.stub.Point
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.extension.sharedPreferences
import com.zhufucdev.motion_emulator.provider.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.jvm.optionals.getOrNull

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ProviderTest {
    @Test
    fun useServerAndClient() {
        useServerClient(false)
    }

    @Test
    fun useServerAndClientTls() {
        useServerClient(true)
    }

    private fun useServerClient(tls: Boolean) {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.sharedPreferences().edit {
            putString("provider_port", "20230")
            putBoolean("provider_tls", tls)
        }

        Scheduler.init(appContext)
        val targetEmulation = randomEmulation()
        Scheduler.emulation = targetEmulation

        runBlocking(Dispatchers.IO) {
            val id = NanoIdUtils.randomNanoId()
            WsServer(port = 20230, useTls = tls).connect(id) {
                assertEquals(targetEmulation, emulation.getOrNull())
                sendStarted(EmulationInfo(10.0, 10.0, id))
                repeat(10) {
                    sendProgress(Intermediate(Point.zero, it * 1.0, (it + 1) / 10f))
                    delay(1000)
                }
            }.close()
        }

        Scheduler.stop(appContext)
    }
}

private fun randomEmulation() = Emulation(
    trace = Trace(NanoIdUtils.randomNanoId(), NanoIdUtils.randomNanoId(), emptyList()),
    motion = EmptyBox(),
    cells = EmptyBox(),
    velocity = 3.0,
    repeat = 3,
    satelliteCount = 100
)