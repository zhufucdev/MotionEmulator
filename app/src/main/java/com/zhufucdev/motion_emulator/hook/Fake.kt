package com.zhufucdev.motion_emulator.hook

import android.hardware.Sensor
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.amap.api.maps.AMapUtils
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.Moment
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random


fun Point.android(provider: String = LocationManager.GPS_PROVIDER): Location {
    val result = Location(provider).apply {
        // fake some data
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            elapsedRealtimeUncertaintyNanos = 5000.0 + (Random.nextDouble() - 0.5) * 1000
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = Random.nextFloat() * 10
        }
        accuracy = Random.nextFloat() * 10
    }

    result.latitude = latitude
    result.longitude = longitude

    return result
}

/**
 * Result for [Trace.at]
 *
 * @param point the interpolated point
 * @param index the bigger index between which the point was interpolated
 * @param totalLength length of the [Trace] used to interpolate
 * @param cache some distance cache humans don't really care
 */
data class TraceInterp(val point: Point, val index: Int, val totalLength: Double, val cache: List<Double>)

/**
 * Interpolate a point, give [progress] valued
 * between 0 and 1
 *
 * The point may have never been drawn
 *
 * @param from if this algorithm is called many times in an increasing [progress] manner,
 * its last result can be used to help calculate faster
 * @see [TraceInterp]
 */
fun Trace.at(progress: Float, from: TraceInterp? = null): TraceInterp {
    if (progress >= 1) {
        return TraceInterp(points.last(), points.lastIndex, 0.0, emptyList())
    } else if (progress < 0) {
        return TraceInterp(points.first(), 0, 0.0, emptyList())
    }

    var totalLength = 0.0
    val cache = if (from == null || from.cache.isEmpty()) {
        buildList {
            add(0.0)
            for (i in 1 until points.size) {
                totalLength += AMapUtils.calculateLineDistance(points[i].toLatLng(), points[i - 1].toLatLng())
                add(totalLength)
            }
        }
    } else {
        totalLength = from.cache.last()
        from.cache
    }
    val required = totalLength * progress
    val range = if (from == null) {
        1 until points.size
    } else {
        from.index + 1 until points.size
    }
    for (i in range) {
        val current = cache[i]
        if (required == current) {
            return TraceInterp(points[i], i, totalLength, cache)
        } else if (current > required) {
            val a = points[i - 1]
            val b = points[i]
            val f = (required - cache[i - 1]) / (cache[i] - cache[i - 1])
            return TraceInterp(
                point = Point(
                    latitude = (b.latitude - a.latitude) * f + a.latitude,
                    longitude = (b.longitude - a.longitude) * f + a.longitude
                ),
                index = i,
                totalLength, cache
            )
        }
    }
    return TraceInterp(points.last(), points.lastIndex, totalLength, cache)
}

/**
 * Result for [Motion.at]
 *
 * @param moment the interpolated data
 * @param index bigger index between which the moment was interpolated
 */
data class MotionInterp(val moment: Moment, val index: Int)

/**
 * Interpolate a moment, given a [progress] valued between
 * 0 and 1
 *
 * Note that step data are not present and the returned
 * value may never exist
 *
 * @see [MotionInterp]
 */
suspend fun Motion.at(progress: Float, from: Int = 0): MotionInterp {
    val duration = moments.last().elapsed - moments.first().elapsed
    val elapsed = duration * progress
    val data = hashMapOf<Int, FloatArray>()
    for (i in from + 1 until moments.size) {
        val current = moments[i]
        if (current.data.containsKey(Sensor.TYPE_STEP_COUNTER) || current.data.containsKey(Sensor.TYPE_STEP_DETECTOR)
            && current.data.size <= 2
        ) {
            continue
        }
        var last: Moment? = null
        for (j in i - 1 downTo 0) {
            if (moments[j].data.keys.containsAll(current.data.keys)) {
                last = moments[j]
                break
            }
        }
        if (last == null) {
            continue // to ensure sensor types are aligned
        }

        fun interp(period: Float, pass: Float, type: Int, current: FloatArray, last: FloatArray) =
            when (type) {
                Sensor.TYPE_STEP_DETECTOR -> null
                Sensor.TYPE_STEP_COUNTER -> null // filter out these types
                else -> (current - last) * (pass / period) + current
            }

        suspend fun align(intendedType: Int): Map<Int, FloatArray> {
            var left: Moment? = null
            var right: Moment? = null
            return coroutineScope {
                val forward = launch {
                    // combo forwards
                    for (j in i - 1 downTo 0) {
                        if (moments[j].data.keys.contains(intendedType)) {
                            left = moments[j]
                            break
                        }
                    }
                }
                val backward = launch {
                    // combo backwards
                    for (j in i + 1 until moments.lastIndex) {
                        if (moments[j].data.keys.contains(intendedType)) {
                            right = moments[j]
                            break
                        }
                    }
                }
                forward.join()
                backward.join()
                if (left == null || right == null) return@coroutineScope emptyMap<Int, FloatArray>()

                val period = right!!.elapsed - left!!.elapsed
                val pass = elapsed - left!!.elapsed
                val result = hashMapOf<Int, FloatArray>()
                right!!.data.forEach { (e, v) ->
                    result[e] = interp(period, pass, e, v, left!!.data[e]!!) ?: return@forEach
                }
                result
            }
        }

        if (elapsed > last.elapsed && elapsed <= current.elapsed) {
            val period = current.elapsed - last.elapsed
            val pass = elapsed - last.elapsed
            current.data.forEach { (e, v) ->
                data[e] = interp(period, pass, e, v, last.data[e]!!) ?: return@forEach
            }

            sensorsInvolved.forEach { s ->
                if (s == Sensor.TYPE_STEP_DETECTOR || s == Sensor.TYPE_STEP_COUNTER) return@forEach
                if (s !in current.data.keys && s !in data.keys) {
                    val aligned = align(s)
                    data.putAll(aligned)
                }
            }

            return MotionInterp(
                moment = Moment(
                    elapsed = elapsed,
                    data = data
                ),
                index = i
            )
        }
    }
    return MotionInterp(Moment(elapsed, data), moments.lastIndex)
}

/**
 * Valid part is partition of a [Motion]
 * that presents stable motion data
 *
 * This method will only trim start and end
 */
fun Motion.validPart(): Motion {
    val detector = sensorsInvolved.contains(Sensor.TYPE_STEP_DETECTOR)
    val counter = sensorsInvolved.contains(Sensor.TYPE_STEP_COUNTER)
    val acc = sensorsInvolved.contains(Sensor.TYPE_ACCELEROMETER)
    val linear = sensorsInvolved.contains(Sensor.TYPE_LINEAR_ACCELERATION)
    if (!detector && !counter && !acc && !linear) {
        return this // no enough data
    }

    fun lookup(reversed: Boolean): Int {
        var detectorFlag = false
        for (i in moments.indices.let { if (reversed) it.reversed() else it }) {
            val moment = moments[i]
            if (detector && !detectorFlag && moment.data[Sensor.TYPE_STEP_DETECTOR]?.first() == 1F) {
                detectorFlag = true
            }

            val others =
                counter && moment.data.containsKey(Sensor.TYPE_STEP_COUNTER)
                        || linear && moment.data[Sensor.TYPE_LINEAR_ACCELERATION]?.length()?.let { it < 1F } == true
                        || acc
                        && moment.data[Sensor.TYPE_ACCELEROMETER]?.filterHighPass()?.length()?.let { it < 1F } == true

            val flag =
                detector && detectorFlag && others || !detector && others

            if (flag) {
                return i
            }
        }
        return if (reversed) moments.lastIndex else 0
    }

    val start = lookup(false)
    val end = lookup(false)

    return Motion(
        id = id,
        time = time,
        moments = moments.subList(start, end),
        sensorsInvolved = sensorsInvolved
    )
}

fun Motion.estimateSpeed(): Double? {
    fun containsType(type: Int) = sensorsInvolved.contains(type)
    val counter = containsType(Sensor.TYPE_STEP_COUNTER)
    val detector = containsType(Sensor.TYPE_STEP_DETECTOR)
    if (!counter && !detector)
        return null //TODO use more sensor types

    var lastMoment: Moment? = null
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

    return sum / count
}
