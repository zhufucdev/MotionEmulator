package com.zhufucdev.motion_emulator.hook_frontend

import android.util.Log
import com.zhufucdev.motion_emulator.data.CellTimeline
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import java.util.concurrent.FutureTask

data class Emulation(
    val trace: Trace,
    val motion: Motion,
    val cells: CellTimeline,
    val velocity: Double,
    val repeat: Int,
    val satelliteCount: Int
)

data class Intermediate(val location: Point, val elapsed: Double, val progress: Float)

data class EmulationInfo(val duration: Double, val length: Double)

object Scheduler {
    private var fts = mutableSetOf<FutureTask<Unit>>()
    private val stateListeners = mutableSetOf<(Boolean) -> Unit>()
    private val intermediateListeners = mutableSetOf<(Intermediate) -> Unit>()

    private var mEmulation: Emulation? = null
    private var mInfo: EmulationInfo? = null
    private var mIntermediate: Intermediate? = null

    var intermediate: Intermediate?
        set(value) {
            if (value != null) {
                intermediateListeners.forEach { it.invoke(value) }
            }
            mIntermediate = value
        }
        get() = mIntermediate
    var info: EmulationInfo?
        set(value) {
            mInfo = value
            Log.d("Scheduler", "info updated with $value")
            stateListeners.forEach { it.invoke(emulation != null) }
        }
        get() = mInfo

    var emulation: Emulation?
        set(value) {
            mEmulation = value
            if (value == null) {
                mInfo = null
            }
            fts.forEach { it.run() }
            stateListeners.forEach { it.invoke(value != null) }
        }
        get() = mEmulation

    fun queue(): Emulation? {
        val ft = FutureTask({}, Unit)
        fts.add(ft)
        ft.get()
        return emulation
    }

    fun onEmulationStateChanged(l: (Boolean) -> Unit): ListenCallback {
        stateListeners.add(l)

        return object : ListenCallback {
            override fun cancel() {
                stateListeners.remove(l)
            }
        }
    }

    fun addIntermediateListener(l: (Intermediate) -> Unit): ListenCallback {
        intermediateListeners.add(l)

        return object : ListenCallback {
            override fun cancel() {
                intermediateListeners.remove(l)
            }
        }
    }
}

interface ListenCallback {
    fun cancel()
}