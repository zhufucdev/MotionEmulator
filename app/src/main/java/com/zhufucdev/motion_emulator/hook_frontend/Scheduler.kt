package com.zhufucdev.motion_emulator.hook_frontend

import android.util.Log
import com.zhufucdev.motion_emulator.data.*
import kotlinx.serialization.Serializable
import kotlin.coroutines.suspendCoroutine

data class Emulation(
    val trace: Trace,
    val motion: Box<Motion>,
    val cells: Box<CellTimeline>,
    val velocity: Double,
    val repeat: Int,
    val satelliteCount: Int
)

@Serializable
data class EmulationRef(
    val trace: String,
    val motion: String,
    val cells: String,
    val velocity: Double,
    val repeat: Int,
    val satelliteCount: Int
)

data class Intermediate(val location: Point, val elapsed: Double, val progress: Float)

data class EmulationInfo(val duration: Double, val length: Double, val owner: String)

object Scheduler {
    private val emulationQueue = mutableMapOf<String, (Emulation?) -> Unit>()
    private val stateListeners = mutableSetOf<(String, Boolean) -> Unit>()
    private val intermediateListeners = mutableSetOf<(String, Intermediate) -> Unit>()

    private var mEmulation: Emulation? = null
    private val mInfo: MutableMap<String, EmulationInfo> = hashMapOf()
    private val mIntermediate: MutableMap<String, Intermediate> = hashMapOf()

    fun setIntermediate(id: String, info: Intermediate?) {
        if (info != null) {
            mIntermediate[id] = info
            intermediateListeners.forEach { it.invoke(id, info) }
        } else {
            mIntermediate.remove(id)
        }
    }

    val intermediate: Map<String, Intermediate> get() = mIntermediate

    fun setInfo(id: String, info: EmulationInfo?) {
        if (info != null) {
            mInfo[id] = info
            Log.d("Scheduler", "info[$id] updated with $info")
        } else {
            mInfo.remove(id)
            emulationQueue[id]?.invoke(null)
            emulationQueue.remove(id)
            Log.d("Scheduler", "info[$id] has been removed")
        }
        stateListeners.forEach { it.invoke(id, info != null) }
    }

    val info: Map<String, EmulationInfo> get() = mInfo

    var emulation: Emulation?
        set(value) {
            mEmulation = value
            if (value == null) {
                mInfo.clear()
            }
            emulationQueue.forEach { (_, queue) -> queue.invoke(value) }
            emulationQueue.clear()
        }
        get() = mEmulation

    suspend fun queue(id: String): Emulation? = suspendCoroutine { c ->
        emulationQueue[id] = { e ->
            c.resumeWith(Result.success(e))
        }
    }

    fun onEmulationStateChanged(l: (String, Boolean) -> Unit): ListenCallback {
        stateListeners.add(l)

        return object : ListenCallback {
            override fun cancel() {
                if (!stateListeners.contains(l)) {
                    throw IllegalStateException("Already cancelled")
                }

                stateListeners.remove(l)
            }

            override fun resume() {
                if (stateListeners.contains(l)) {
                    throw IllegalStateException("Already resumed")
                }

                stateListeners.add(l)
            }
        }
    }

    fun addIntermediateListener(l: (String, Intermediate) -> Unit): ListenCallback {
        intermediateListeners.add(l)

        return object : ListenCallback {
            override fun cancel() {
                if (!intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already cancelled")
                }

                intermediateListeners.remove(l)
            }

            override fun resume() {
                if (intermediateListeners.contains(l)) {
                    throw IllegalStateException("Already resumed")
                }

                intermediateListeners.add(l)
            }
        }
    }
}

interface ListenCallback {
    fun cancel()
    fun resume()
}