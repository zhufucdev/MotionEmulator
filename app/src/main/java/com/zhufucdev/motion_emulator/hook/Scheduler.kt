package com.zhufucdev.motion_emulator.hook

import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.os.SystemClock
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.data.Intermediate
import com.zhufucdev.motion_emulator.toPoint
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class)
object Scheduler {
    private const val TAG = "Scheduler"
    private const val LOCALHOST = "http://127.0.0.1"
    private val id = NanoIdUtils.randomNanoId()
    private lateinit var packageName: String

    private val jobs = arrayListOf<Job>()
    private val httpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
    }

    fun PackageParam.init(context: Context) {
        this@Scheduler.packageName = context.applicationContext.packageName
        GlobalScope.launch {
            eventLoop()
            loggerI(tag = TAG, msg = "Provider offline. Waiting for data channel broadcast")
            dataChannel.wait("provider_online") {
                GlobalScope.launch {
                    eventLoop()
                }
            }
        }
        loggerI(TAG, "Event loop started")
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
        val queryArgs = arrayOf(id)
        // query existing state
        httpClient.get("$LOCALHOST/current").apply {
            if (status == HttpStatusCode.OK) {
                val emulation = body<Emulation>()
                startEmulation(emulation)
            } else {
                return
            }
        }

        // enter event loop
        while (true) {
             val res = httpClient.get("$LOCALHOST/next/${id}")

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
    private fun startEmulation(emulation: Emulation): Boolean {
        loggerI(tag = TAG, msg = "Emulation started")

        length = emulation.trace.circumference(MapProjector)
        duration = length / emulation.velocity // in seconds
        this.satellites = satellites

        val scope = CoroutineScope(Dispatchers.Default)
        hooking = true
        scope.async {
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
            updateState(false)
            scope.cancel()
        }.let {
            jobs.add(it)
        }

        return true
    }

    private var stepsCount: Int = -1
    private fun CoroutineScope.startStepsEmulation(motion: Box<Motion>, velocity: Double): Job? {
        SensorHooker.toggle = motion.status

        return if (motion.value != null && motion.value.sensorsInvolved.any { it in stepSensors }) {
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
            var traceInterp = trace.saltedPoints.at(0F, MapProjector)
            while (hooking && progress <= 1) {
                val interp = trace.saltedPoints.at(progress, MapProjector, traceInterp)
                traceInterp = interp
                mLocation = interp.point.toPoint(trace.coordinateSystem)
                LocationHooker.raise(interp.point.toPoint())

                notifyProgress()
                delay(1000)
            }
        }

    private fun CoroutineScope.startCellEmulation(cells: Box<CellTimeline>): Job? {
        CellHooker.toggle = cells.status

        return if (cells.value != null) {
            launch {
                var ptr = 0
                val timespan = cells.value.moments.timespan()
                while (hooking && progress <= 1 && ptr < cells.value.moments.size) {
                    val current = cells.value.moments[ptr]
                    mCellMoment = current
                    CellHooker.raise(current)

                    if (cells.value.moments.size == 1) {
                        delay(duration.seconds) // halt
                    } else {
                        if (ptr == cells.value.moments.lastIndex - 1) {
                            break
                        }
                        val pause =
                            (cells.value.moments[ptr].elapsed - cells.value.moments[ptr + 1].elapsed) /
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
        httpClient.post("$LOCALHOST/indeterminate/${id}") {
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
        loggerI(TAG, "Updated state[$id] = $running")
        if (running) {
            val status = EmulationInfo(duration, length, packageName)
            httpClient.post("$LOCALHOST/state/${id}/running") {
                setBody(status)
            }
        } else {
            httpClient.get("$LOCALHOST/state/${id}/stopped")
        }
    }
}