package com.zhufucdev.stub

import com.google.android.gms.maps.model.LatLng
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.maps.android.SphericalUtil
import kotlin.math.*

/**
 * Coordinate transformer
 *
 * @see [toIdeal]
 * @see [toTarget]
 */
interface Projector {
    /**
     * Squared distance between two [Vector2D] in **target** plane
     *
     * Direct norm can be resolved by [Vector2D.lenTo] or taking a [BypassProjector]
     */
    fun Vector2D.distance(other: Vector2D): Double

    /**
     * Squared distance between two [Vector2D] in **ideal** plane
     *
     * See [Projector.distance] if you prefer target plane
     */
    fun Vector2D.distanceIdeal(other: Vector2D): Double

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

    override fun Vector2D.distance(other: Vector2D): Double =
        calculateLineDistance(this, other)

    override fun Vector2D.distanceIdeal(other: Vector2D): Double =
        SphericalUtil.computeDistanceBetween(LatLng(x, y), LatLng(other.x, other.y))

    override fun Vector2D.toTarget(): Vector2D = this + cache[this]

    override fun Vector2D.toIdeal(): Vector2D = this - cache[this]

    /* Decompiled code which nobody cares */
    private fun calculateLineDistance(var0: Vector2D, var1: Vector2D): Double {
        return try {
            var var2: Double = var0.y
            var var4: Double = var0.x
            var var6: Double = var1.y
            var var8: Double = var1.x
            var2 *= 0.01745329251994329
            var4 *= 0.01745329251994329
            var6 *= 0.01745329251994329
            var8 *= 0.01745329251994329
            val var10 = sin(var2)
            val var12 = sin(var4)
            val var14 = cos(var2)
            val var16 = cos(var4)
            val var18 = sin(var6)
            val var20 = sin(var8)
            val var22 = cos(var6)
            val var24 = cos(var8)
            val var28 = DoubleArray(3)
            val var29 = DoubleArray(3)
            var28[0] = var16 * var14
            var28[1] = var16 * var10
            var28[2] = var12
            var29[0] = var24 * var22
            var29[1] = var24 * var18
            var29[2] = var20
            (asin(sqrt((var28[0] - var29[0]) * (var28[0] - var29[0]) + (var28[1] - var29[1]) * (var28[1] - var29[1]) + (var28[2] - var29[2]) * (var28[2] - var29[2])) / 2.0) * 1.27420015798544E7)
        } catch (var26: Throwable) {
            var26.printStackTrace()
            0.0
        }
    }
}