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
import com.zhufucdev.data.*
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.data.*
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Point.amap(provider: String = LocationManager.GPS_PROVIDER) =
    AMapLocation(android(provider, mapProjector = MapProjector))

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

    if (sum < 0 || sum.isNaN() || count <= 0) return null
    return sum / count
}

fun Motion.estimateTimespan(): Duration {
    if (moments.size < 2) return 0.seconds
    return (moments.last().elapsed - moments.first().elapsed * 1.0).seconds
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
