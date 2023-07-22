package com.zhufucdev.ws_plugin.hook

import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.Method
import com.zhufucdev.xposed.AbstractScheduler
import com.zhufucdev.xposed.PREFERENCE_NAME_BRIDGE
import com.zhufucdev.xposed.TrustAllX509TrustManager
import com.zhufucdev.xposed.hooking
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import kotlin.time.Duration.Companion.seconds

object Scheduler : AbstractScheduler() {
    private const val TAG = "Scheduler"
    private const val LOCALHOST = "localhost"
    private var port = 20230
    private var tls = false

    private val providerAddr get() = (if (tls) "https://" else "http://") + "$LOCALHOST:$port"

    private val httpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json()
            }

            engine {
                // disable certificate verification
                if (tls) {
                    sslManager = { connection ->
                        connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                            init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                        }.socketFactory
                    }
                }
                connectTimeout = 0
                socketTimeout = 0
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun PackageParam.initialize() {
        val prefs = prefs(PREFERENCE_NAME_BRIDGE)
        port = prefs.getString("provider_port").toIntOrNull() ?: 2023
        tls = prefs.getBoolean("provider_tls", true)
        val useTestProvider = prefs.getBoolean("use_test_provider_effective")
        hookingMethod =
            if (!useTestProvider) Method.XPOSED_ONLY
            else prefs.getString("method", "xposed_only").let {
                Method.valueOf(it.uppercase())
            }

        loggerI(tag = TAG, "service listens on $providerAddr")

        GlobalScope.launch {
            startServer()
        }
    }

    private suspend fun startServer() {
        var logged = false

        while (true) {
            try {
                // query existing state
                httpClient.get("${providerAddr}/current").apply {
                    if (status == HttpStatusCode.OK) {
                        val emulation = body<Emulation>()
                        startEmulation(emulation)
                    }
                }
            } catch (e: ConnectException) {
                // ignored
            }

            loggerI(TAG, "current emulation vanished. Entering event loop...")

            eventLoop()
            if (!logged) {
                loggerI(tag = TAG, msg = "Provider offline. Waiting for data channel to become online")
                logged = true
            }
            delay(1.seconds)
        }
    }

    private suspend fun eventLoop() = coroutineScope {
        try {
            loggerI(TAG, "Event loop started on $port")
            var currentEmu: Job? = null

            while (true) {
                val res = httpClient.get("$providerAddr/next/$id")

                when (res.status) {
                    HttpStatusCode.OK -> {
                        hooking = true
                        val emulation = res.body<Emulation>()
                        currentEmu = launch {
                            startEmulation(emulation)
                        }
                    }

                    HttpStatusCode.NoContent -> {
                        hooking = false
                        currentEmu?.cancelAndJoin()
                        loggerI(tag = TAG, msg = "Emulation cancelled")
                    }

                    else -> {
                        return@coroutineScope
                    }
                }
            }
        } catch (e: ConnectException) {
            // ignored, or more specifically, treat it as offline
            // who the fuck cares what's going on
        }
    }

    override suspend fun updateState(running: Boolean) {
        runCatching {
            if (running) {
                val status = EmulationInfo(duration, length, packageName)
                httpClient.post("$providerAddr/state/$id/running") {
                    contentType(ContentType.Application.Json)
                    setBody(status)
                }
            } else {
                httpClient.get("$providerAddr/state/$id/stopped")
            }
            loggerD(TAG, "Updated state[$id] = $running")
        }
    }

    override suspend fun notifyProgress() {
        runCatching {
            httpClient.post("$providerAddr/intermediate/$id") {
                contentType(ContentType.Application.Json)
                setBody(
                    Intermediate(
                        progress = progress,
                        location = location,
                        elapsed = elapsed / 1000.0
                    )
                )
            }
        }
    }
}