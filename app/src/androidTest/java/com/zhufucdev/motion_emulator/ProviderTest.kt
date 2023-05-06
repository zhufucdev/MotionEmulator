package com.zhufucdev.motion_emulator

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.factory.prefs
import com.zhufucdev.data.EmptyBox
import com.zhufucdev.data.Emulation
import com.zhufucdev.data.Trace
import com.zhufucdev.motion_emulator.hook.TrustAllX509TrustManager
import com.zhufucdev.motion_emulator.provider.Scheduler
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import javax.net.ssl.SSLContext

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
        appContext.prefs().edit { putBoolean("provider_tls", tls) }

        Scheduler.init(appContext)
        val targetEmulation = randomEmulation()
        Scheduler.emulation = targetEmulation

        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json()
            }

            engine {
                // disable certificate verification
                sslManager = { connection ->
                    connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                        init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                    }.socketFactory
                }
            }
        }

        val protocol = if (tls) "https" else "http"
        val addr = "localhost:2023"

        runBlocking(Dispatchers.IO) {
            assertEquals(targetEmulation, client.get("$protocol://$addr/current").body<Emulation>())
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