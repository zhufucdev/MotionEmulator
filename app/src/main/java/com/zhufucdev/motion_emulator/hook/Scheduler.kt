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
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

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
                        val cells = cursor.getString(2)
                        val velocity = cursor.getDouble(3)
                        val repeat = cursor.getInt(4)
                        val satellites = cursor.getInt(5)
                        val started = startEmulation(trace, motion, cells, velocity, repeat, satellites)
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

    private var start = 0L
    private val elapsed get() = SystemClock.elapsedRealtime() - start

    /**
     * Duration of this emulation in seconds
     */
    private var duration = -1.0
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
    val location get() = mLocation ?: Point(39.989410, 116.480881)
    val cells get() = mCellMoment ?: CellMoment(0F)

    private val stepSensors = intArrayOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)
    private fun startEmulation(
        traceData: String,
        motionData: String,
        cellsData: String,
        velocity: Double,
        repeat: Int,
        satellites: Int
    ): Boolean {
        val trace = Json.decodeFromString(Trace.serializer(), traceData)
        val motion = Json.decodeFromString(Motion.serializer(), motionData).validPart()
        val cells = Json.decodeFromString(CellTimeline.serializer(), cellsData)

        val fullTrace = trace.at(0F)
        duration = fullTrace.totalLen / velocity // in seconds
        start = SystemClock.elapsedRealtime()
        this.satellites = satellites

        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            for (i in 0 until repeat) {
                val jobs = mutableSetOf<Job>()
                startStepsEmulation(motion)?.let { jobs.add(it) }
                startMotionSimulation(motion)?.let { jobs.add(it) }
                startTraceEmulation(trace, fullTrace).let { jobs.add(it) }
                startCellEmulation(cells).let { jobs.add(it) }

                launch {
                    // to clear current jobs
                    jobs.addAll(jobs)
                    jobs.forEach { it.join() }
                    jobs.removeAll(jobs.toSet())

                    updateState(false)
                    scope.cancel()
                }
            }
        }

        return true
    }

    private fun CoroutineScope.startStepsEmulation(motion: Motion): Job? =
        if (stepSensors.any { motion.sensorsInvolved.contains(it) }) {
            val stepMoments = motion.moments.filter { m -> stepSensors.any { m.data.containsKey(it) } }
            val pause = (duration / stepMoments.size).seconds
            launch {
                var stepsCount = Random.nextFloat() * 5000 + 2000 // beginning with a random steps count
                while (progress <= 1) {
                    var index = 0
                    while (hooking && index < stepMoments.size) {
                        val moment = stepMoments[index]
                        moment.data[Sensor.TYPE_STEP_COUNTER] = floatArrayOf(stepsCount++)
                        SensorHooker.raise(moment)
                        index++

                        notifyProgress()
                        delay(pause)
                    }
                }
            }
        } else {
            null
        }

    private fun CoroutineScope.startMotionSimulation(motion: Motion): Job? =
        if (motion.sensorsInvolved.any { !stepSensors.contains(it) }) {
            launch {
                // data other than steps
                while (progress <= 1) {
                    var lastIndex = 0
                    while (hooking && lastIndex < motion.moments.size) {
                        val interp = motion.at(progress, lastIndex)
                        SensorHooker.raise(interp.moment)
                        lastIndex = interp.index

                        notifyProgress()
                        delay(500)
                    }
                }
            }
        } else {
            null
        }

    private fun CoroutineScope.startTraceEmulation(trace: Trace, opti: TraceInterp): Job =
        launch {
            var traceInterp = opti
            while (hooking && progress <= 1) {
                val interp = trace.at(progress, traceInterp)
                traceInterp = interp
                mLocation = interp.point
                LocationHooker.raise(interp.point)

                notifyProgress()
                delay(1000)
            }
        }

    private fun CoroutineScope.startCellEmulation(cells: CellTimeline): Job =
        launch {
            var ptr = 0
            val timespan = cells.moments.timespan()
            while (hooking && progress <= 1 && ptr < cells.moments.size) {
                val current = cells.moments[ptr]
                mCellMoment = current
                CellHooker.raise(current)

                if (cells.moments.size == 1) {
                    delay(duration.seconds) // halt
                } else {
                    if (ptr == cells.moments.lastIndex - 1) {
                        break
                    }
                    val pause = (cells.moments[ptr].elapsed - cells.moments[ptr + 1].elapsed) / timespan * duration
                    ptr++
                    delay(pause.seconds)
                }
            }
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