package com.zhufucdev.motion_emulator.hook

import android.content.Context
import android.hardware.Sensor
import android.os.SystemClock
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.data.*
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.toPoint
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import java.net.ConnectException
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
object Scheduler {
    private const val TAG = "Scheduler"
    private const val LOCALHOST = "127.0.0.1"
    private val id = NanoIdUtils.randomNanoId()
    private var port = 2023
    private var tls = false
    private lateinit var packageName: String

    private val providerAddr get() = (if (tls) "https://" else "http://") + "$LOCALHOST:$port"

    private val jobs = arrayListOf<Job>()
    private val httpClient by lazy(tls) {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json()
            }

            if (tls) {
                engine {
                    // disable certificate verification
                    sslManager = { connection ->
                        connection.sslSocketFactory = SSLContext.getInstance("TLS").apply {
                            init(null, arrayOf(TrustAllX509TrustManager), SecureRandom())
                        }.socketFactory
                    }
                }
            }
        }
    }

    fun PackageParam.init(context: Context) {
        this@Scheduler.packageName = context.applicationContext.packageName
        port = prefs.getString("provider_port").toIntOrNull() ?: 2023
        tls = prefs.getBoolean("provider_tls", true)

        GlobalScope.launch {
            loggerI(tag = TAG, "Listen event loop on port $port, tls = $tls")

            var logged = false
            // query existing state
            httpClient.get("$providerAddr/current").apply {
                if (status == HttpStatusCode.OK) {
                    val emulation = body<Emulation>()
                    launch {
                        startEmulation(emulation)
                    }
                }
            }

            while (true) {
                eventLoop()
                if (!logged) {
                    loggerI(tag = TAG, msg = "Provider offline. Waiting for data channel to become online")
                    logged = true
                }
                delay(1.seconds)
            }
        }
    }

    /**
     * To initialize the scheduler
     */
    val hook = object : YukiBaseHooker() {
        override fun onHook() {
            onAppLifecycle {
                onCreate {
                    init(applicationContext)
                }
            }
        }
    }

    private suspend fun eventLoop() {
        try {
            loggerI(TAG, "Event loop started on $port")

            while (true) {
                val res = httpClient.get("$providerAddr/next/${id}")

                when (res.status) {
                    HttpStatusCode.OK -> {
                        val emulation = res.body<Emulation>()
                        startEmulation(emulation)
                    }

                    HttpStatusCode.NoContent -> {
                        hooking = false
                        jobs.joinAll()
                        loggerI(tag = TAG, msg = "Emulation stopped")
                    }

                    else -> {
                        return
                    }
                }
            }
        } catch (e: ConnectException) {
            // ignored, or more specifically, treat it as offline
            // who the fuck cares what's going on
        }
    }

    private var start = 0L
    private val elapsed get() = SystemClock.elapsedRealtime() - start

    /**
     * Duration of this emulation in seconds
     */
    private var duration = -1.0
    private var length = 0.0
    private var mLocation: Point? = null
    private var mCellMoment: CellMoment? = null

    /**
     * How many satellites to simulate
     *
     * 0 to not simulate
     */
    var satellites: Int = 0
        private set
    private val progress get() = (elapsed / duration / 1000).toFloat()
    val location get() = mLocation ?: Point.zero
    val cells get() = mCellMoment ?: CellMoment(0F)
    val motion = MotionMoment(0F, mutableMapOf())

    private val stepSensors = intArrayOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)
    private suspend fun startEmulation(emulation: Emulation): Boolean {
        loggerI(tag = TAG, msg = "Emulation started")

        length = emulation.trace.length(MapProjector)
        duration = length / emulation.velocity // in seconds
        this.satellites = emulation.satelliteCount

        hooking = true
        updateState(true)
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            for (i in 0 until emulation.repeat) {
                start = SystemClock.elapsedRealtime()

                val jobs = mutableSetOf<Job>()
                startStepsEmulation(emulation.motion, emulation.velocity)?.let { jobs.add(it) }
                startMotionSimulation(emulation.motion)?.let { jobs.add(it) }
                startTraceEmulation(emulation.trace).let { jobs.add(it) }
                startCellEmulation(emulation.cells)?.let { jobs.add(it) }

                jobs.addAll(jobs)
                jobs.joinAll()
                jobs.clear()

                if (!hooking) break
            }
            hooking = false
            scope.cancel()
            updateState(false)
        }.let {
            jobs.add(it)
        }

        return true
    }

    private var stepsCount: Int = -1
    private fun CoroutineScope.startStepsEmulation(motion: Box<Motion>, velocity: Double): Job? {
        SensorHooker.toggle = motion.status

        return if (motion.value != null && motion.value!!.sensorsInvolved.any { it in stepSensors }) {
            val pause = (1.2 / velocity).seconds
            launch {
                if (stepsCount == -1) {
                    stepsCount =
                        (Random.nextFloat() * 5000).toInt() + 2000 // beginning with a random steps count
                }
                while (hooking && progress <= 1) {
                    val moment =
                        MotionMoment(
                            elapsed / 1000F,
                            mutableMapOf(
                                Sensor.TYPE_STEP_COUNTER to floatArrayOf(1F * stepsCount++),
                                Sensor.TYPE_STEP_DETECTOR to floatArrayOf(1F)
                            )
                        )
                    SensorHooker.raise(moment)

                    notifyProgress()
                    delay(pause)
                }
            }
        } else {
            SensorHooker.toggle = motion.status
            null
        }
    }

    private fun CoroutineScope.startMotionSimulation(motion: Box<Motion>): Job? {
        SensorHooker.toggle = motion.status
        val partial = motion.value?.validPart()

        return if (partial != null && partial.sensorsInvolved.any { it !in stepSensors }) {
            launch {
                // data other than steps
                while (hooking && progress <= 1) {
                    var lastIndex = 0
                    while (hooking && lastIndex < partial.moments.size && progress <= 1) {
                        val interp = partial.at(progress, lastIndex)

                        SensorHooker.raise(interp.moment)
                        lastIndex = interp.index

                        notifyProgress()
                        delay(100)
                    }
                }
            }
        } else {
            null
        }
    }

    private fun CoroutineScope.startTraceEmulation(trace: Trace): Job =
        launch {
            val salted = trace.generateSaltedPoints(MapProjector)
            var traceInterp = salted.at(0F, MapProjector)
            while (hooking && progress <= 1) {
                val interp = salted.at(progress, MapProjector, traceInterp)
                traceInterp = interp
                mLocation = interp.point.toPoint(trace.coordinateSystem)
                LocationHooker.raise(interp.point.toPoint())

                notifyProgress()
                delay(1000)
            }
        }

    private fun CoroutineScope.startCellEmulation(cells: Box<CellTimeline>): Job? {
        CellHooker.toggle = cells.status
        val value = cells.value

        return if (value != null) {
            launch {
                var ptr = 0
                val timespan = value.moments.timespan()
                while (hooking && progress <= 1 && ptr < value.moments.size) {
                    val current = value.moments[ptr]
                    mCellMoment = current
                    CellHooker.raise(current)

                    if (value.moments.size == 1) {
                        delay(duration.seconds) // halt
                    } else {
                        if (ptr == value.moments.lastIndex - 1) {
                            break
                        }
                        val pause =
                            (value.moments[ptr].elapsed - value.moments[ptr + 1].elapsed) /
                                    timespan * duration
                        ptr++
                        delay(pause.seconds)
                    }
                }
            }
        } else {
            null
        }
    }

    private suspend fun notifyProgress() {
        httpClient.post("$providerAddr/indeterminate/${id}") {
            contentType(ContentType.Application.Json)
            setBody(
                Intermediate(
                    progress = progress,
                    location = mLocation!!,
                    elapsed = elapsed / 1000.0
                )
            )
        }
    }

    private suspend fun updateState(running: Boolean) {
        if (running) {
            val status = EmulationInfo(duration, length, packageName)
            httpClient.post("$providerAddr/state/${id}/running") {
                contentType(ContentType.Application.Json)
                setBody(status)
            }
        } else {
            httpClient.get("$providerAddr/state/${id}/stopped")
        }
        loggerD(TAG, "Updated state[$id] = $running")
    }
}