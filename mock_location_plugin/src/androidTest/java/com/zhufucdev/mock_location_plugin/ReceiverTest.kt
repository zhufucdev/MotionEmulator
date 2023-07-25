package com.zhufucdev.mock_location_plugin

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.Point
import com.zhufucdev.stub_plugin.WsServer
import com.zhufucdev.stub_plugin.connect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ReceiverTest {
    @Test
    fun useMockLocationProvider() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        MockLocationProvider.init(appContext, WsServer(20230, true))
        runBlocking {
            MockLocationProvider.emulate()
        }
    }

    @Test
    fun useAbstractionLayer() {
        val server = WsServer(20230, true)
        runBlocking(Dispatchers.IO) {
            server.connect(NanoIdUtils.randomNanoId()) {
                sendStarted(EmulationInfo(20.0, 10.0, BuildConfig.APPLICATION_ID))
                repeat(10) {
                    sendProgress(Intermediate(Point.zero, it * 2.0, it / 10f))
                    delay(2000)
                }
            }
        }
    }
}