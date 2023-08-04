package com.zhufucdev.stub_plugin

import android.os.SystemClock
import com.zhufucdev.stub.Box
import com.zhufucdev.stub.CellTimeline
import com.zhufucdev.stub.Emulation
import com.zhufucdev.stub.EmulationInfo
import com.zhufucdev.stub.Motion
import com.zhufucdev.stub.Trace
import com.zhufucdev.stub.length
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * An scheduler is the first thing that picks the
 * [Emulation] request, perceives it and deploy
 * to concrete actions
 */
abstract class AbstractScheduler {
    abstract val packageName: String

    var isWorking = false
        private set

    /**
     * Duration of this emulation in seconds
     */
    protected var duration = -1.0
    protected var length = 0.0

    /**
     * How many satellites to simulate
     *
     * 0 to not simulate
     */
    var satellites: Int = 0
        private set

    private var loopStart = 0L
    val loopElapsed get() = SystemClock.elapsedRealtime() - loopStart
    protected val loopProgress get() = (loopElapsed / duration / 1000).toFloat()

    protected suspend fun ServerScope.startEmulation(emulation: Emulation) {
        length = emulation.trace.length()
        duration = length / emulation.velocity // in seconds
        satellites = emulation.satelliteCount

        sendStarted(EmulationInfo(duration, length, packageName))
        onEmulationStarted(emulation)
        for (i in 0 until emulation.repeat) {
            loopStart = SystemClock.elapsedRealtime()
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
        }

        onEmulationCompleted(emulation)
    }

    open fun onEmulationStarted(emulation: Emulation) {
        isWorking = true
    }

    open fun onEmulationCompleted(emulation: Emulation) {
        isWorking = false
    }

    open suspend fun ServerScope.startStepsEmulation(motion: Box<Motion>, velocity: Double) {
    }

    open suspend fun ServerScope.startMotionSimulation(motion: Box<Motion>) {
    }

    open suspend fun ServerScope.startTraceEmulation(trace: Trace) {
    }

    open suspend fun ServerScope.startCellEmulation(cells: Box<CellTimeline>) {
    }
}
