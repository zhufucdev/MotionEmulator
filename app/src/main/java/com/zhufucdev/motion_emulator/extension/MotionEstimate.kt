package com.zhufucdev.motion_emulator.extension

import android.hardware.Sensor
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.me.stub.MotionMoment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Motion.estimateSpeed(): Double? {
    fun containsType(type: Int) = sensorsInvolved.contains(type)
    val counter = containsType(Sensor.TYPE_STEP_COUNTER)
    val detector = containsType(Sensor.TYPE_STEP_DETECTOR)
    if (!counter && !detector)
        return null //TODO use more sensor types

    var lastMoment: MotionMoment? = null
    var sum = 0.0
    var count = 0
    if (counter) {
        // if counter is available, drop detector
        for (current in moments) {
            if (current.data.containsKey(Sensor.TYPE_STEP_COUNTER)) {
                val last = lastMoment
                if (last == null) {
                    lastMoment = current
                    continue
                }
                val steps =
                    current.data[Sensor.TYPE_STEP_COUNTER]!!.first() - last.data[Sensor.TYPE_STEP_COUNTER]!!.first()
                val time = current.elapsed - last.elapsed
                sum += 1.2 * steps / time
                count++
            }
        }
    } else {
        // if not, relay on detector
        for (current in moments) {
            if (current.data.containsKey(Sensor.TYPE_STEP_DETECTOR)) {
                val last = lastMoment
                if (last == null) {
                    lastMoment = current
                    continue
                }
                val time = current.elapsed - last.elapsed
                sum += 1.2 / time
                count++
            }
        }
    }

    if (sum < 0 || sum.isNaN() || count <= 0) return null
    return sum / count
}

fun Motion.estimateTimespan(): Duration {
    if (moments.size < 2) return 0.seconds
    return (moments.last().elapsed - moments.first().elapsed * 1.0).seconds
}

