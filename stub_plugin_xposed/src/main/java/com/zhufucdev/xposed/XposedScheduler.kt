package com.zhufucdev.xposed

import android.hardware.Sensor
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.stub.Box
import com.zhufucdev.stub.CellMoment
import com.zhufucdev.stub.CellTimeline
import com.zhufucdev.stub.Intermediate
import com.zhufucdev.stub.MapProjector
import com.zhufucdev.stub.Method
import com.zhufucdev.stub.Motion
import com.zhufucdev.stub.MotionMoment
import com.zhufucdev.stub.Point
import com.zhufucdev.stub.Trace
import com.zhufucdev.stub.at
import com.zhufucdev.stub.generateSaltedTrace
import com.zhufucdev.stub.toPoint
import com.zhufucdev.stub_plugin.AbstractScheduler
import com.zhufucdev.stub_plugin.ServerScope
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

abstract class XposedScheduler : AbstractScheduler() {
    override val packageName: String
        get() = packageNameHooked ?: throw IllegalStateException("Not initialized")
    private var packageNameHooked: String? = null

    val sensorHooker = SensorHooker(this)
    val locationHooker = LocationHooker(this)
    val cellHooker = CellHooker(this)

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
            packageNameHooked = packageName
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

    protected var mLocation: Point? = null
    private var mCellMoment: CellMoment? = null

    private val intermediate
        get() = Intermediate(
            progress = loopProgress,
            location = location,
            elapsed = loopElapsed / 1000.0
        )

    val location get() = mLocation ?: Point.zero
    val cells get() = mCellMoment ?: CellMoment(0F)
    val motion = MotionMoment(0F, mutableMapOf())

    private val stepSensors = intArrayOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)

    private var stepsCount: Int = -1
    override suspend fun ServerScope.startStepsEmulation(motion: Box<Motion>, velocity: Double) {
        sensorHooker.toggle = motion.status

        if (motion.value != null && motion.value!!.sensorsInvolved.any { it in stepSensors }) {
            val pause = (1.2 / velocity).seconds
            if (stepsCount == -1) {
                stepsCount =
                    (Random.nextFloat() * 5000).toInt() + 2000 // beginning with a random steps count
            }
            while (isWorking && loopProgress <= 1) {
                val moment =
                    MotionMoment(
                        loopElapsed / 1000F,
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

    override suspend fun ServerScope.startMotionSimulation(motion: Box<Motion>) {
        sensorHooker.toggle = motion.status
        val partial = motion.value?.validPart()

        if (partial != null && partial.sensorsInvolved.any { it !in stepSensors }) {
            // data other than steps
            while (isWorking && loopProgress <= 1) {
                var lastIndex = 0
                while (isWorking && lastIndex < partial.moments.size && loopProgress <= 1) {
                    val interp = partial.at(loopProgress, lastIndex)

                    sensorHooker.raise(interp.moment)
                    lastIndex = interp.index

                    sendProgress(intermediate)
                    delay(100)
                }
            }
        }
    }

    override suspend fun ServerScope.startTraceEmulation(trace: Trace) {
        val salted = trace.generateSaltedTrace()
        var traceInterp = salted.at(0F, MapProjector)
        while (isWorking && loopProgress <= 1) {
            val interp = salted.at(loopProgress, MapProjector, traceInterp)
            traceInterp = interp
            mLocation = interp.point.toPoint(trace.coordinateSystem)
            locationHooker.raise(interp.point.toPoint())

            sendProgress(intermediate)
            delay(1000)
        }
    }

    override suspend fun ServerScope.startCellEmulation(cells: Box<CellTimeline>) {
        cellHooker.toggle = cells.status
        val value = cells.value

        if (value != null) {
            var ptr = 0
            val timespan = value.moments.timespan()
            while (isWorking && loopProgress <= 1 && ptr < value.moments.size) {
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
