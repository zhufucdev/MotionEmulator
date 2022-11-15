package com.zhufucdev.motion_emulator.hook

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telephony.CellIdentityCdma
import android.telephony.CellIdentityGsm
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellLocation
import android.telephony.CellSignalStrengthNr
import android.telephony.NeighboringCellInfo
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import android.util.Log
import androidx.core.os.bundleOf
import com.amap.api.location.AMapLocation
import com.amap.api.maps.AMapUtils
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.round
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
        accuracy = 1F
    }

    val fixed = MapFixUtil.transform(latitude, longitude)
    result.latitude = fixed[0]
    result.longitude = fixed[1]

    return result
}

fun Point.amap(): AMapLocation = AMapLocation(android())

/**
 * Result for [Trace.at]
 *
 * @param point the interpolated point
 * @param index the bigger index between which the point was interpolated
 * @param totalDeg length of the [Trace] used to interpolate, in degrees
 * @param totalLen length of the [Trace], in meters
 * @param cache some distance cache humans don't really care
 */
data class TraceInterp(
    val point: Point,
    val index: Int,
    val totalDeg: Double,
    val totalLen: Double,
    val cache: List<Double>
)

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
        return TraceInterp(points.last(), points.lastIndex, 0.0, 0.0, emptyList())
    } else if (progress < 0) {
        return TraceInterp(points.first(), 0, 0.0, 0.0, emptyList())
    }

    var totalDeg = 0.0
    var totalLen = 0.0
    val cache = if (from == null || from.cache.isEmpty()) {
        buildList {
            add(0.0)
            for (i in 1 until points.size) {
                totalDeg += points[i].lenTo(points[i - 1])
                totalLen += AMapUtils.calculateLineDistance(points[i].toLatLng(), points[i - 1].toLatLng())
                add(totalDeg)
            }
        }
    } else {
        totalDeg = from.totalDeg
        totalLen = from.totalLen
        from.cache
    }
    val required = totalDeg * progress
    val range = if (from == null) {
        1 until points.size
    } else {
        from.index + 1 until points.size
    }
    for (i in range) {
        val current = cache[i]
        if (required == current) {
            return TraceInterp(points[i], i, totalDeg, totalLen, cache)
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
                totalDeg, totalLen, cache
            )
        }
    }
    return TraceInterp(points.last(), points.lastIndex, totalDeg, totalLen, cache)
}

/**
 * Result for [Motion.at]
 *
 * @param moment the interpolated data
 * @param index bigger index between which the moment was interpolated
 */
data class MotionInterp(val moment: MotionMoment, val index: Int)

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
        var last: MotionMoment? = null
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
            var left: MotionMoment? = null
            var right: MotionMoment? = null
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
                moment = MotionMoment(
                    elapsed = elapsed,
                    data = data
                ),
                index = i
            )
        }
    }
    return MotionInterp(MotionMoment(elapsed, data), moments.lastIndex)
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

    return sum / count
}

/**
 * Get the geometric center of a trace.
 * @param length length of the trace if known. Will
 * help the algorithm run faster
 */
fun Trace.center(length: Double = 0.0): Point {
    val calcSamples = if (length <= 0) {
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += AMapUtils.calculateLineDistance(points[i].toLatLng(), points[i - 1].toLatLng())
        }
        sum
    } else {
        length
    } / 50
    val sampleCount = (if (calcSamples < 10) 10 else calcSamples).toInt()
    Log.d("center", "length = $length, samples = $sampleCount")
    var lastInterp = at(0F)
    var laSum = 0.0
    var lgSum = 0.0
    for (i in 1..sampleCount) {
        val sample = at(i * 1F / sampleCount, lastInterp)
        laSum += sample.point.latitude
        lgSum += sample.point.longitude
        lastInterp = sample
    }

    return Point(laSum / sampleCount, lgSum / sampleCount)
}

interface Moment {
    val elapsed: Float
}

/**
 * Get the timespan of a timeline
 *
 * @return duration in seconds
 */
fun List<Moment>.timespan(): Float {
    if (isEmpty()) return 0F
    if (size == 1) return first().elapsed
    return last().elapsed - first().elapsed
}

@SuppressLint("NewApi")
fun CellInfo.cellLocation(): CellLocation? =
    when (val id = cellIdentity) {
        is CellIdentityCdma ->
            CdmaCellLocation(
                bundleOf(
                    "baseStationId" to id.basestationId,
                    "baseStationLatitude" to id.latitude,
                    "baseStationLongitude" to id.longitude,
                    "systemId" to id.systemId,
                    "networkId" to id.networkId
                )
            )

        is CellIdentityGsm ->
            GsmCellLocation(
                bundleOf(
                    "lac" to id.lac,
                    "cid" to id.cid,
                    "psc" to id.psc
                )
            )

        else -> null
    }

@SuppressLint("NewApi")
fun CellMoment.cellLocation(): CellLocation? {
    if (location != null) return location
    return if (cell.isNotEmpty()) {
        cell.first().cellLocation()
    } else {
        null
    }
}

@SuppressLint("NewApi")
fun CellMoment.neighboringInfo(): List<NeighboringCellInfo> {
    if (neighboring.isNotEmpty()) return neighboring
    if (cell.isNotEmpty()) {
        return cell.mapNotNull {
            when (it) {
                is CellInfoCdma -> NeighboringCellInfo(it.cellSignalStrength.dbm, it.cellIdentity.basestationId)
                is CellInfoGsm -> NeighboringCellInfo(it.cellSignalStrength.rssi, it.cellIdentity.cid)
                is CellInfoLte -> NeighboringCellInfo(it.cellSignalStrength.rssi, it.cellIdentity.ci)
                is CellInfoWcdma -> NeighboringCellInfo(it.cellSignalStrength.dbm, it.cellIdentity.cid)
                else -> null
            }
        }
    }
    return emptyList()
}

@SuppressLint("MissingPermission")
fun PhoneStateListener.treatWith(moment: CellMoment, mode: Int) {
    var mask = PhoneStateListener.LISTEN_CELL_INFO
    if (mode and mask == mask) {
        onCellInfoChanged(moment.cell)
    }
    mask = PhoneStateListener.LISTEN_CELL_LOCATION
    if (mode and mask == mask) {
        onCellLocationChanged(moment.cellLocation())
    }
}

@SuppressLint("NewApi", "MissingPermission")
fun TelephonyCallback.treatWith(moment: CellMoment) {
    if (this is TelephonyCallback.CellInfoListener) {
        onCellInfoChanged(moment.cell)
    }
    if (this is TelephonyCallback.CellLocationListener) {
        moment.cellLocation()?.let {
            onCellLocationChanged(it)
        }
    }
}
