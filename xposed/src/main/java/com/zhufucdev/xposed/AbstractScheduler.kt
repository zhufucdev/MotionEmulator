package com.zhufucdev.xposed

import android.content.Context
import android.hardware.Sensor
import android.os.SystemClock
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.*
import kotlinx.coroutines.*
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

abstract class AbstractScheduler {
    private lateinit var sensorHooker: SensorHooker
    private lateinit var locationHooker: LocationHooker
    private lateinit var cellHooker: CellHooker

    protected lateinit var packageName: String
        private set
    val id: String = NanoIdUtils.randomNanoId()
    var hookingMethod: Method = Method.XPOSED_ONLY
        protected set

    fun PackageParam.init(context: Context) {
        this@AbstractScheduler.packageName = context.applicationContext.packageName

        initialize()

        if (!hookingMethod.involveXposed) {
            return
        }

        sensorHooker = SensorHooker(this@AbstractScheduler)
        locationHooker = LocationHooker(this@AbstractScheduler)
        cellHooker = CellHooker(this@AbstractScheduler)
        loadHooker(sensorHooker)
        loadHooker(locationHooker)
        loadHooker(cellHooker)
    }

    abstract fun PackageParam.initialize()

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

    private var start = 0L
    val elapsed get() = SystemClock.elapsedRealtime() - start

    /**
     * Duration of this emulation in seconds
     */
    protected var duration = -1.0
    protected var length = 0.0
    protected var mLocation: Point? = null
    private var mCellMoment: CellMoment? = null

    /**
     * How many satellites to simulate
     *
     * 0 to not simulate
     */
    var satellites: Int = 0
        private set
    private val progress get() = (elapsed / duration / 1000).toFloat()
    private val intermediate
        get() = Intermediate(
            progress = progress,
            location = location,
            elapsed = elapsed / 1000.0
        )

    val location get() = mLocation ?: Point.zero
    val cells get() = mCellMoment ?: CellMoment(0F)
    val motion = MotionMoment(0F, mutableMapOf())

    private val stepSensors = intArrayOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)
    protected suspend fun startEmulation(emulation: Emulation) {
        length = emulation.trace.length()
        duration = length / emulation.velocity // in seconds
        satellites = emulation.satelliteCount

        hooking = true
        notifyStarted(EmulationInfo(duration, length, packageName))
        for (i in 0 until emulation.repeat) {
            start = SystemClock.elapsedRealtime()
            coroutineScope {
                launch {
                    startStepsEmulation(emulation.motion, emulation.velocity)
                }
                launch {
                    startMotionSimulation(emulation.motion)
                }
                launch {
                    startTraceEmulation(emulation.trace)
                }
                launch {
                    startCellEmulation(emulation.cells)
                }
            }

            if (!hooking) break
        }
        hooking = false
        notifyStopped()
    }

    private var stepsCount: Int = -1
    private suspend fun startStepsEmulation(motion: Box<Motion>, velocity: Double) {
        sensorHooker.toggle = motion.status

        if (motion.value != null && motion.value!!.sensorsInvolved.any { it in stepSensors }) {
            val pause = (1.2 / velocity).seconds
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
                sensorHooker.raise(moment)

                notifyProgress(intermediate)
                delay(pause)
            }
        }
    }

    private suspend fun startMotionSimulation(motion: Box<Motion>) {
        sensorHooker.toggle = motion.status
        val partial = motion.value?.validPart()

        if (partial != null && partial.sensorsInvolved.any { it !in stepSensors }) {
            // data other than steps
            while (hooking && progress <= 1) {
                var lastIndex = 0
                while (hooking && lastIndex < partial.moments.size && progress <= 1) {
                    val interp = partial.at(progress, lastIndex)

                    sensorHooker.raise(interp.moment)
                    lastIndex = interp.index

                    notifyProgress(intermediate)
                    delay(100)
                }
            }
        }
    }

    private suspend fun startTraceEmulation(trace: Trace) {
        val salted = trace.generateSaltedTrace(MapProjector)
        var traceInterp = salted.at(0F, MapProjector)
        while (hooking && progress <= 1) {
            val interp = salted.at(progress, MapProjector, traceInterp)
            traceInterp = interp
            mLocation = interp.point.toPoint(trace.coordinateSystem)
            locationHooker.raise(interp.point.toPoint())

            notifyProgress(intermediate)
            delay(1000)
        }
    }

    private suspend fun startCellEmulation(cells: Box<CellTimeline>) {
        cellHooker.toggle = cells.status
        val value = cells.value

        if (value != null) {
            var ptr = 0
            val timespan = value.moments.timespan()
            while (hooking && progress <= 1 && ptr < value.moments.size) {
                val current = value.moments[ptr]
                mCellMoment = current
                cellHooker.raise(current)

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
    }

    abstract suspend fun notifyProgress(intermediate: Intermediate)
    abstract suspend fun notifyStarted(info: EmulationInfo)
    abstract suspend fun notifyStopped()
}

/**
 * This variable determines whether the sensor hooks work.
 *
 * Defaults to false. Use content provider to set.
 */
var hooking = false