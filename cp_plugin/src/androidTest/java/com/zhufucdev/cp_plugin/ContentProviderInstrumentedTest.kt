package com.zhufucdev.cp_plugin

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhufucdev.cp_plugin.provider.bridge
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub_plugin.MePlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import kotlin.time.Duration.Companion.seconds

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ContentProviderInstrumentedTest {
    @Test
    fun useSelfhosted() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val scope = CoroutineScope(Dispatchers.Default)

        scope.launch {
            val server = MePlugin.queryServer(context)
            server.bridge()
        }

        useConnect()
    }

    @Test
    fun useConnect() {
        val cr = InstrumentationRegistry.getInstrumentation().context.contentResolver
        runBlocking {
            delay(1.seconds)
            ContentProviderServer(PROVIDER_AUTHORITY, cr).connect("whatever") {
                sendStarted(EmulationInfo(10.0, 10.0, BuildConfig.APPLICATION_ID))
            }
        }
    }
}