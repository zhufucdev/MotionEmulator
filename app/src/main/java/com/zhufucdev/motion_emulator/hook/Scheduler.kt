package com.zhufucdev.motion_emulator.hook

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.hardware.Sensor
import android.net.Uri
import android.os.SystemClock
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.loggerI
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.hook_frontend.AUTHORITY
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

object Scheduler {
    private const val TAG = "Scheduler"

    private lateinit var eventResolver: ContentResolver
    private val nextUri = Uri.parse("content://$AUTHORITY/next")
    private val stateUri = Uri.parse("content://$AUTHORITY/state")
    private val currentUri = Uri.parse("content://$AUTHORITY/current")

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
            onAppLifecycle {
                onCreate {
                    init(applicationContext)
                }
            }
        }
    }

    private suspend fun eventLoop() {
        fun handleStart(cursor: Cursor) {
            cursor.moveToNext()
            val trace = cursor.getString(0)
            val motion = cursor.getString(1)
            val cells = cursor.getString(2)
            val velocity = cursor.getDouble(3)
            val repeat = cursor.getInt(4)
            val satellites = cursor.getInt(5)
            val started = startEmulation(trace, motion, cells, velocity, repeat, satellites)
            updateState(started)
        }

        // query existing state
        eventResolver.query(currentUri, null, null, null, null)?.use { cursor ->
            cursor.moveToNext()
            if (cursor.getInt(0) == EMULATION_START) {
                handleStart(cursor)
            }
        }

        // enter event loop
        while (true) {
            eventResolver.query(nextUri, null, null, null, null)?.use { cursor ->
                cursor.moveToNext()
                when (cursor.getInt(0)) {
                    EMULATION_START -> handleStart(cursor)
                    EMULATION_STOP -> {
                        hooking = false
                        jobs.joinAll()
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
    val location get() = mLocation ?: Point(39.989410, 116.480881)
    val cells get() = mCellMoment ?: CellMoment(0F)
    val motion = MotionMoment(0F, mutableMapOf())

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
        val motion = Box.decodeFromString<Motion>(motionData)
        val cells = Box.decodeFromString<CellTimeline>(cellsData)

        val fullTrace = trace.at(0F)
        length = fullTrace.totalLen
        duration = length / velocity // in seconds
        this.satellites = satellites

        val scope = CoroutineScope(Dispatchers.Default)
        hooking = true
        scope.async {
            for (i in 0 until repeat) {
                start = SystemClock.elapsedRealtime()

                val jobs = mutableSetOf<Job>()
                startStepsEmulation(motion, velocity)?.let { jobs.add(it) }
                startMotionSimulation(motion)?.let { jobs.add(it) }
                startTraceEmulation(trace, fullTrace).let { jobs.add(it) }
                startCellEmulation(cells)?.let { jobs.add(it) }

                jobs.addAll(jobs)
                jobs.joinAll()
                jobs.clear()

                if (!hooking) break
            }
            hooking = false
            updateState(false)
            scope.cancel()
        }.start()

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


    private fun notifyProgress() {
        val values = ContentValues()
        values.put("progress", progress)
        values.put("pos_la", mLocation!!.latitude)
        values.put("pos_lg", mLocation!!.longitude)
        values.put("elapsed", elapsed / 1000.0)
        eventResolver.update(stateUri, values, "progress", null)
    }

    private fun updateState(running: Boolean) {
        val values = ContentValues()
        loggerI(TAG, "updated state = $running")
        values.put("state", running)
        if (running) {
            values.put("duration", duration)
            values.put("length", length)
        }
        eventResolver.update(stateUri, values, "state", null)
    }
}