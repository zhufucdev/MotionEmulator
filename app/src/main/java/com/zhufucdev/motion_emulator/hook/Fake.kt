@file:Suppress("DEPRECATION")

package com.zhufucdev.motion_emulator.hook

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.telephony.*
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import androidx.core.os.bundleOf
import com.amap.api.location.AMapLocation
import com.amap.api.maps.AMapUtils
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

fun Point.offsetFixed(): Point = with(MapProjector) { toIdeal() }.toPoint()

fun Point.android(
    provider: String = LocationManager.GPS_PROVIDER,
    fixOffset: Boolean = true
): Location {
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


    if (fixOffset) {
        val fixed = offsetFixed()
        result.latitude = fixed.latitude
        result.longitude = fixed.longitude
    } else {
        result.latitude = latitude
        result.longitude = longitude
    }

    return result
}

fun Point.amap(
    provider: String = LocationManager.GPS_PROVIDER,
    fixOffset: Boolean = true
): AMapLocation =
    AMapLocation(android(provider, fixOffset))

/**
 * Result for [ClosedShape.at]
 *
 * @param point the interpolated point
 * @param index the bigger index between which the point was interpolated
 * @param totalLen length of the [ClosedShape] used to interpolate, in target panel's measure
 * @param cache some distance cache humans don't really care
 */
data class ClosedShapeInterp(
    val point: Vector2D,
    val index: Int,
    val totalLen: Double,
    val cache: List<Double>
)

/**
 * Interpolate a point, given [progress] valued
 * between 0 and 1
 *
 * The point may have never been drawn
 *
 * @param from if this algorithm is called many times in an increasing [progress] manner,
 * its last result can be used to help calculate faster
 * @param projector The interpolation will happen on the **ideal** plane. Basically, it's projected to
 * the ideal plane, and back to the target plane
 * @see [ClosedShapeInterp]
 */
fun List<Vector2D>.at(progress: Float, projector: Projector = BypassProjector, from: ClosedShapeInterp? = null): ClosedShapeInterp {
    if (progress > 1) {
        return ClosedShapeInterp(last(), size - 1, 0.0, emptyList())
    } else if (progress < 0) {
        return ClosedShapeInterp(first(), 0, 0.0, emptyList())
    }

    var totalLen = 0.0
    val cache = if (from == null || from.cache.isEmpty()) {
        buildList {
            add(0.0)
            for (i in 1 until this@at.size) {
                totalLen += with(projector) { this@at[i].distance(this@at[i - 1]) }
                add(totalLen)
            }
        }
    } else {
        totalLen = from.totalLen
        from.cache
    }
    val required = totalLen * progress
    val range = if (from == null) {
        1 until this.size
    } else {
        from.index until this.size
    }
    for (i in range) {
        val current = cache[i]
        if (required == current) {
            return ClosedShapeInterp(this[i], i, totalLen, cache)
        } else if (current > required) {
            val a = with(projector) { this@at[i - 1].toIdeal() }
            val b = with(projector) { this@at[i].toIdeal() }
            val f = (required - cache[i - 1]) / (cache[i] - cache[i - 1])
            return ClosedShapeInterp(
                point = with(projector) {
                    Vector2D(
                        x = (b.x - a.x) * f + a.x,
                        y = (b.y - a.y) * f + a.y
                    ).toTarget()
                },
                index = i,
                totalLen, cache
            )
        }
    }
    return ClosedShapeInterp(this.last(), this.lastIndex, totalLen, cache)
}

fun Trace.length(): Double {
    var sum = 0.0
    for (i in 1 until points.size) {
        sum += AMapUtils.calculateLineDistance(points[i - 1].toLatLng(), points[i].toLatLng())
    }
    return sum
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
fun Motion.at(progress: Float, from: Int = 0): MotionInterp {
    fun interp(progr: Float, current: FloatArray, last: FloatArray) =
        (current - last) * progr + current

    val duration = moments.last().elapsed - moments.first().elapsed
    val start = moments.first().elapsed
    val targetElapsed = duration * progress
    val data = hashMapOf<Int, FloatArray>()

    val stepTypes = listOf(Sensor.TYPE_STEP_COUNTER, Sensor.TYPE_STEP_DETECTOR)
    val targetTypes = sensorsInvolved - stepTypes

    var minIndex = moments.lastIndex

    targetTypes.forEach { type ->
        var last: MotionMoment? = null
        for (i in from until moments.size) {
            val current = moments[i]
            if (!current.data.containsKey(type)) {
                continue
            }

            if (current.elapsed - start <= targetElapsed) {
                last = current
                continue
            }

            // current is later than target elapsed
            if (from > 0) {
                if (last == null) {
                    // try to find a moment with specific type
                    // and is earlier than the current one
                    for (j in from - 1 downTo 0) {
                        if (moments[i].data.containsKey(type) && moments[i].elapsed < targetElapsed) {
                            last = moments[i]
                            break
                        }
                    }
                }
                if (last != null) {
                    data[type] = interp(
                        (targetElapsed - last.elapsed + start) / (current.elapsed - last.elapsed),
                        current.data[type]!!,
                        last.data[type]!!
                    )
                }
            } else {
                // if the current one is the first element
                // do a reverse interpolation
                for (j in i + 1 until moments.size) {
                    val next = moments[j]
                    if (next.data.containsKey(type) && next.elapsed - start > targetElapsed) {
                        data[type] = interp(
                            (targetElapsed - next.elapsed + start) / (next.elapsed - current.elapsed),
                            next.data[type]!!,
                            current.data[type]!!
                        )
                        break
                    }
                }
            }

            if (i < minIndex) {
                minIndex = i
            }
            break
        }
    }
    return MotionInterp(MotionMoment(targetElapsed, data), minIndex)
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
                        || linear && moment.data[Sensor.TYPE_LINEAR_ACCELERATION]?.length()
                    ?.let { it < 1F } == true
                        || acc
                        && moment.data[Sensor.TYPE_ACCELEROMETER]?.filterHighPass()?.length()
                    ?.let { it < 1F } == true

            val flag =
                detector && detectorFlag && others || !detector && others

            if (flag) {
                return i
            }
        }
        return if (reversed) moments.lastIndex else 0
    }

    val start = lookup(false)
    val end = lookup(true)

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

    if (sum < 0 || sum.isNaN()) return null
    return sum / count
}

@OptIn(ExperimentalTime::class)
fun Motion.estimateTimespan(): Duration {
    if (moments.size < 2) return 0.seconds
    return (moments.last().elapsed - moments.first().elapsed * 1.0).seconds
}

/**
 * Get the geometric center of a [ClosedShape].
 *
 * @param projector The calculation will happen on the **ideal**
 * plane. Basically, a point is projected to the ideal plane, then
 * back to target plane.
 */
fun ClosedShape.center(projector: Projector = BypassProjector): Vector2D {
    if (points.isEmpty()) throw IllegalArgumentException("points is empty")

    var sum = points.first()
    for (i in 1 until points.size) sum += with(projector) { points[i].toIdeal() }

    return with(projector) { Vector2D(sum.x / points.size, sum.y / points.size).toTarget() }
}

/**
 * Get circumference of a [ClosedShape].
 *
 * @param projector The calculation will happen on the **ideal**
 * plane. Basically, a point is projected to the ideal plane, then
 * back to target plane.
 */
fun ClosedShape.circumference(projector: Projector = BypassProjector): Double {
    var c = 0.0
    if (points.isEmpty()) return c
    for (i in 0 until points.lastIndex - 1) {
        c += with(projector) { points[i].distance(points[i + 1]) }
    }
    if (points.size > 2) c += with(projector) { points[0].distance(points.last()) }
    return c
}

/**
 * Get the time span of a timeline
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

@Suppress("DEPRECATION")
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
                is CellInfoCdma -> NeighboringCellInfo(
                    it.cellSignalStrength.dbm,
                    it.cellIdentity.basestationId
                )
                is CellInfoGsm -> NeighboringCellInfo(
                    it.cellSignalStrength.rssi,
                    it.cellIdentity.cid
                )
                is CellInfoLte -> NeighboringCellInfo(
                    it.cellSignalStrength.rssi,
                    it.cellIdentity.ci
                )
                is CellInfoWcdma -> NeighboringCellInfo(
                    it.cellSignalStrength.dbm,
                    it.cellIdentity.cid
                )
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
