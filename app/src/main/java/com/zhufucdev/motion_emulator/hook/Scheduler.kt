package com.zhufucdev.motion_emulator.hook

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.net.Uri
import android.os.SystemClock
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.factory.classOf
import com.highcapable.yukihookapi.hook.log.loggerE
import com.highcapable.yukihookapi.hook.log.loggerI
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.hook_frontend.AUTHORITY
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong
import kotlin.random.Random

object Scheduler {
    private const val TAG = "Scheduler"

    private lateinit var eventResolver: ContentResolver
    private val nextUri = Uri.parse("content://$AUTHORITY/next")
    private val stateUri = Uri.parse("content://$AUTHORITY/state")

    private val jobs = arrayListOf<Job>()

    @OptIn(DelicateCoroutinesApi::class)
    fun init(context: Context) {
        if (::eventResolver.isInitialized) return
        eventResolver = context.contentResolver
        GlobalScope.launch {
            eventLoop()
        }
        loggerI(TAG, "Event loop started")
    }

    /**
     * To initialize the scheduler
     */
    val hook = object : YukiBaseHooker() {
        override fun onHook() {
            classOf<ContextWrapper>().hook {
                injectMember {
                    method {
                        name = "getApplicationContext"
                        emptyParam()
                        returnType = classOf<Context>()
                    }

                    afterHook {
                        val ctx = result<Context>()
                        if (ctx == null) {
                            loggerE(tag = TAG, "Failed to initialize: context unavailable")
                            return@afterHook
                        }
                        init(ctx)
                    }
                }
            }
        }
    }

    private suspend fun eventLoop() {
        while (true) {
            eventResolver.query(nextUri, null, null, null, null)?.use { cursor ->
                cursor.moveToNext()
                when (cursor.getInt(0)) {
                    COMMAND_EMULATION_START -> {
                        cursor.moveToNext()
                        hooking = true
                        val trace = cursor.getString(0)
                        val motion = cursor.getString(1)
                        val velocity = cursor.getDouble(2)
                        val started = startEmulation(trace, motion, velocity)
                        updateState(started)
                    }

                    COMMAND_EMULATION_STOP -> {
                        hooking = false
                        jobs.forEach {
                            it.join()
                        }
                        loggerI(tag = TAG, msg = "emulation stopped")
                        updateState(false)
                    }
                }
            }
        }
    }

    private var elapsed = 0L
    private var duration = -1.0
    private var mLocation: Point? = null
    private val progress get() = (elapsed / duration / 1000).toFloat()
    val location get() = mLocation ?: Point(39.989410, 116.480881)

    private fun startEmulation(traceData: String, motionData: String, velocity: Double): Boolean {
        val trace = Json.decodeFromString(Trace.serializer(), traceData)
        val motion = Json.decodeFromString(Motion.serializer(), motionData).validPart()
        val steps = intArrayOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)

        var traceInterp = trace.at(0F)
        duration = traceInterp.totalLen / velocity // in seconds

        val start = SystemClock.elapsedRealtime()
        fun updateElapsed() {
            elapsed = SystemClock.elapsedRealtime() - start
        }

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            val jobs = arrayListOf<Job>()
            if (steps.any { motion.sensorsInvolved.contains(it) }) {
                val stepMoments = motion.moments.filter { m -> steps.any { m.data.containsKey(it) } }
                val pause = (duration / stepMoments.size * 1000).roundToLong()
                jobs.add(
                    launch {
                        var stepsCount = Random.nextFloat() * 5000 + 2000 // beginning with a random steps count
                        while (progress <= 1) {
                            var index = 0
                            while (hooking && index < stepMoments.size) {
                                updateElapsed()
                                val moment = stepMoments[index]
                                moment.data[Sensor.TYPE_STEP_COUNTER] = floatArrayOf(stepsCount++)
                                SensorHooker.raise(moment)
                                index++

                                notifyProgress()
                                delay(pause)
                            }
                        }
                    }
                )
            }

            if (motion.sensorsInvolved.any { !steps.contains(it) }) {
                jobs.add(
                    launch {
                        // data other than steps
                        while (progress <= 1) {
                            var lastIndex = 0
                            while (hooking && lastIndex < motion.moments.size) {
                                updateElapsed()
                                val interp = motion.at(progress, lastIndex)
                                SensorHooker.raise(interp.moment)
                                lastIndex = interp.index

                                notifyProgress()
                                delay(500)
                            }
                        }
                    }
                )
            }

            jobs.add(
                launch {
                    // trace
                    while (hooking && progress <= 1) {
                        updateElapsed()
                        val interp = trace.at(progress, traceInterp)
                        traceInterp = interp
                        mLocation = interp.point
                        LocationHooker.raise(interp.point)

                        notifyProgress()
                        delay(1000)
                    }
                }
            )

            launch {
                // to clear current jobs
                jobs.addAll(jobs)
                jobs.forEach { it.join() }
                jobs.removeAll(jobs.toSet())

                updateState(false)
                scope.cancel()
            }
        }

        return true
    }

    private fun notifyProgress() {
        val values = ContentValues()
        values.put("progress", progress)
        values.put("pos_la", mLocation!!.latitude)
        values.put("pos_lg", mLocation!!.longitude)
        eventResolver.update(stateUri, values, "progress", null)
    }

    private fun updateState(running: Boolean) {
        val values = ContentValues()
        values.put("state", running)
        eventResolver.update(stateUri, values, "state", null)
    }
}