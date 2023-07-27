package com.zhufucdev.motion_emulator

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.extension.toFixed
import com.zhufucdev.motion_emulator.provider.Scheduler
import com.zhufucdev.motion_emulator.provider.configureSsl
import com.zhufucdev.motion_emulator.provider.eventServer
import com.zhufucdev.stub.EmptyBox
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.Trace
import com.zhufucdev.stub_plugin.WsServer
import com.zhufucdev.stub_plugin.connect
import io.ktor.server.application.Application
import io.ktor.server.engine.applicationEngineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ProviderUnitTest {
    @Test
    fun local_loopback() {
        Scheduler.emulation = randomEmulation()
        val server = embeddedServer(Netty, applicationEngineEnvironment {
            configureSsl(3000)
            module(Application::eventServer)
        })
        server.start()

        Scheduler.addIntermediateListener { s, intermediate ->
            println("Progress received, ${intermediate.progress.toFixed(2)}")
        }

        val coroutine = CoroutineScope(Dispatchers.IO)
        val client = coroutine.launch {
            sendAndReceive(WsServer(port = 3000, useTls = true))
        }
        coroutine.launch {
            delay(2500)
            Scheduler.cancelAll()
            delay(1000)
            Scheduler.startAll()
        }
        runBlocking {
            client.join()
        }
        server.stop()
    }

    @Test
    fun try_server() {
        runBlocking {
            sendAndReceive(WsServer("192.168.1.8", port = 20230, useTls = false))
        }
    }

    private suspend fun sendAndReceive(server: WsServer) {
        val id = NanoIdUtils.randomNanoId()
        server.connect(id) {
            sendStarted(EmulationInfo(10.0, 10.0, id))
            repeat(10) {
                sendProgress(Intermediate(Point.zero, it * 2.0, (it + 1) / 10f))
                println("Progress sent, ${it + 1} / 10")
                delay(1000)
            }
            close()
        }.close()
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
