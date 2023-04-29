package com.zhufucdev.motion_emulator.data

import android.content.Context
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CoordinateConverter
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.maps.android.SphericalUtil
import com.zhufucdev.motion_emulator.toAmapLatLng
import com.zhufucdev.motion_emulator.lenTo
import com.zhufucdev.motion_emulator.toGoogleLatLng
import com.zhufucdev.motion_emulator.ui.manager.FactorCanvas
import kotlin.math.*

/**
 * Coordinate transformer
 *
 * @see [toIdeal]
 * @see [toTarget]
 */
interface Projector {
    /**
     * Square distances between two [Vector2D] in **target** plane
     *
     * Direct norm can be resolved by [Vector2D.lenTo] or taking a [BypassProjector]
     */
    fun Vector2D.distance(other: Vector2D): Double

    /**
     * Project some [Vector2D] to the target plane
     *
     * A target plane means one the projector targets
     */
    fun Vector2D.toTarget(): Vector2D

    /**
     * Project some [Vector2D] to the ideal plane
     *
     * An ideal plane is an absolutely flat plane, where sum
     * of inner angles of any triangle is 180 degrees
     */
    fun Vector2D.toIdeal(): Vector2D
}

object BypassProjector : Projector {
    override fun Vector2D.distance(other: Vector2D): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    override fun Vector2D.toTarget(): Vector2D = this

    override fun Vector2D.toIdeal(): Vector2D = this
}

/**
 * A [Projector] targeting GCJ-02 coordinating system
 *
 * The ideal plane is WGS-84 coordination
 */
object MapProjector : Projector {
    private const val pi = 3.14159265358979324
    private const val a = 6378245.0
    private const val ee = 0.00669342162296594323

    fun outOfChina(lat: Double, lon: Double): Boolean {
        return if (lon < 72.004 || lon > 137.8347) true else lat < 0.8293 || lat > 55.8271
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0
        ret += (20.0 * sin(y * pi) + 40.0 * sin(y / 3.0 * pi)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * pi) + 320 * sin(y * pi / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * pi) + 20.0 * sin(2.0 * x * pi)) * 2.0 / 3.0
        ret += (20.0 * sin(x * pi) + 40.0 * sin(x / 3.0 * pi)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * pi) + 300.0 * sin(x / 30.0 * pi)) * 2.0 / 3.0
        return ret
    }

    override fun Vector2D.distance(other: Vector2D): Double =
        AMapUtils.calculateLineDistance(this.toAmapLatLng(), other.toAmapLatLng()).toDouble()

    private val cache =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build(
                object : CacheLoader<Vector2D, Vector2D>() {
                    override fun load(key: Vector2D): Vector2D {
                        val x = key.x
                        val y = key.y

                        if (outOfChina(x, y)) return Vector2D.zero
                        var dLat = transformLat(y - 105.0, x - 35.0)
                        var dLon = transformLon(y - 105.0, x - 35.0)
                        val radLat = x / 180 * pi
                        var magic = sin(radLat)
                        magic = 1 - ee * magic * magic
                        val sqrtMagic = sqrt(magic)
                        dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtMagic) * pi)
                        dLon = dLon * 180.0 / (a / sqrtMagic * cos(radLat) * pi)
                        return Vector2D(dLat, dLon)
                    }
                }
            )

    override fun Vector2D.toTarget(): Vector2D = this + cache[this]

    override fun Vector2D.toIdeal(): Vector2D = this - cache[this]
}


/**
 * A [Projector] targeting [FactorCanvas]
 */
class CanvasProjector(scope: DrawScope, val boundLeft: Float, val boundBottom: Float) : Projector {
    val drawingSize = with(scope) { Size(size.width - boundLeft, size.height - boundBottom) }

    override fun Vector2D.distance(other: Vector2D): Double =
        sqrt((x - other.x) * drawingSize.width.pow(2) + (y - other.y) * drawingSize.height.pow(2))

    override fun Vector2D.toTarget(): Vector2D =
        Vector2D(x * drawingSize.width + boundLeft, (1 - y) * drawingSize.height)

    override fun Vector2D.toIdeal(): Vector2D =
        Vector2D((x - boundLeft) / drawingSize.width, 1 - y / drawingSize.height)

}

fun DrawScope.CanvasProjector(boundLeft: Float, boundBottom: Float) =
    CanvasProjector(this, boundLeft, boundBottom)

/**
 * A [Projector] targeting AMap
 *
 * The ideal plane is WGS-84
 *
 * This projector **doesn't** support unversed projection
 */
class AMapProjector(context: Context) : Projector {
    private val cvt = CoordinateConverter(context).from(CoordinateConverter.CoordType.GPS)

    override fun Vector2D.distance(other: Vector2D): Double =
        AMapUtils.calculateLineDistance(this.toAmapLatLng(), other.toAmapLatLng()).toDouble()


    override fun Vector2D.toTarget(): Vector2D =
        cvt.coord(this.toAmapLatLng()).convert().let { Vector2D(it.latitude, it.longitude) }


    override fun Vector2D.toIdeal(): Vector2D =
        throw UnsupportedOperationException("Projection from AMap is not supported")
}