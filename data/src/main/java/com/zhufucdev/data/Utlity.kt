package com.zhufucdev.data

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import java.text.DateFormat
import java.util.*
import kotlin.random.Random

fun DateFormat.dateString(time: Long = System.currentTimeMillis()): String =
    format(Date(time))

const val SERIALIZATION_ID = "com.zhufucdev.motion_emulator"

fun Point.offsetFixed(mapProjector: AbstractMapProjector): Point =
    with(if (coordinateSystem == CoordinateSystem.GCJ02) mapProjector else BypassProjector) { toIdeal() }.toPoint()

fun Point.android(
    provider: String = LocationManager.GPS_PROVIDER,
    speed: Float = 0F,
    mapProjector: AbstractMapProjector
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = 0.1F
            if (speed > 0) {
                speedAccuracyMetersPerSecond = 0.01F
            }
        }
    }


    val fixed = offsetFixed(mapProjector)
    result.latitude = fixed.latitude
    result.longitude = fixed.longitude
    result.speed = speed

    return result
}

fun Trace.generateSaltedPoints(mapProjector: AbstractMapProjector): List<Vector2D> {
    val salt = this.salt
    return if (salt != null) {
        val runtime = salt.runtime()
        val projector =
            if (coordinateSystem == CoordinateSystem.GCJ02) mapProjector else BypassProjector
        points.map {
            runtime.apply(
                point = it,
                projector = projector,
                parent = this
            )
        }
    } else {
        points
    }
}

fun estimateDistance(current: Point, last: Point, mapProjector: AbstractMapProjector) =
    if (current.coordinateSystem == CoordinateSystem.WGS84 && last.coordinateSystem == CoordinateSystem.WGS84) {
        with(mapProjector) {
            current.distanceIdeal(last)
        }
    } else if (current.coordinateSystem == CoordinateSystem.GCJ02 && last.coordinateSystem == CoordinateSystem.GCJ02) {
        with(mapProjector) {
            current.distance(last)
        }
    } else {
        throw IllegalArgumentException("current comes with a different coordination " +
                "system (${current.coordinateSystem.name}) than last")
    }

fun estimateSpeed(current: Pair<Point, Long>, last: Pair<Point, Long>, mapProjector: AbstractMapProjector) =
    estimateDistance(current.first, last.first, mapProjector) / (current.second - last.second) * 1000

/**
 * Length of a trace, including the closing part
 */
fun Trace.length(mapProjector: AbstractMapProjector): Double {
    var sum = 0.0
    for (i in 1 until points.size) {
        sum += estimateDistance(points[i], points[i - 1], mapProjector)
    }
    sum += estimateDistance(points.first(), points.last(), mapProjector)
    return sum
}
