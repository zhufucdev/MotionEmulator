package com.zhufucdev.xposed

import android.hardware.Sensor
import android.os.SystemClock
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.Box
import com.zhufucdev.stub.CellMoment
import com.zhufucdev.stub.CellTimeline
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.MapProjector
import com.zhufucdev.stub.Method
import com.zhufucdev.stub.Motion
import com.zhufucdev.stub.MotionMoment
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.Trace
import com.zhufucdev.stub.at
import com.zhufucdev.stub.generateSaltedTrace
import com.zhufucdev.stub.length
import com.zhufucdev.stub.toPoint
import com.zhufucdev.stub_plugin.ServerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

abstract class AbstractScheduler {
    val sensorHooker = SensorHooker(this)
    val locationHooker = LocationHooker(this)
    val cellHooker = CellHooker(this)

    protected lateinit var packageName: String
        private set
    val id: String = NanoIdUtils.randomNanoId()
    var hookingMethod: Method = Method.XPOSED_ONLY
        private set

    /**
     * Invoked after [getHookingMethod] returns a method with
     * xposed involved
     *
     * @see [Method.involveXposed]
     */
    abstract fun PackageParam.initialize()

    /**
     * Invoked right after the hook is loaded
     */
    abstract fun PackageParam.getHookingMethod(): Method

    /**
     * To initialize the scheduler
     */
    val hook = object : YukiBaseHooker() {
        override fun onHook() {
            this@AbstractScheduler.packageName = packageName
            hookingMethod = getHookingMethod()

            if (!hookingMethod.involveXposed) {
                return
            }

            loadHooker(sensorHooker)
            loadHooker(locationHooker)
            loadHooker(cellHooker)

            initialize()
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
    protected suspend fun ServerScope.startEmulation(emulation: Emulation) {
        length = emulation.trace.length()
        duration = length / emulation.velocity // in seconds
        satellites = emulation.satelliteCount

        hooking = true
        sendStarted(EmulationInfo(duration, length, packageName))
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
    }

    private var stepsCount: Int = -1
    private suspend fun ServerScope.startStepsEmulation(motion: Box<Motion>, velocity: Double) {
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

                sendProgress(intermediate)
                delay(pause)
            }
        }
    }

    private suspend fun ServerScope.startMotionSimulation(motion: Box<Motion>) {
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

                    sendProgress(intermediate)
                    delay(100)
                }
            }
        }
    }

    private suspend fun ServerScope.startTraceEmulation(trace: Trace) {
        val salted = trace.generateSaltedTrace(MapProjector)
        var traceInterp = salted.at(0F, MapProjector)
        while (hooking && progress <= 1) {
            val interp = salted.at(progress, MapProjector, traceInterp)
            traceInterp = interp
            mLocation = interp.point.toPoint(trace.coordinateSystem)
            locationHooker.raise(interp.point.toPoint())

            sendProgress(intermediate)
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
}

/**
 * This variable determines whether the sensor hooks work.
 *
 * Defaults to false. Use content provider to set.
 */
var hooking = false