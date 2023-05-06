package com.zhufucdev.motion_emulator.mock_location_plugin

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import com.zhufucdev.data.AbstractMapProjector
import com.zhufucdev.data.Vector2D
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object MapProjector : AbstractMapProjector() {
    override fun Vector2D.distance(other: Vector2D): Double =
        calculateLineDistance(this, other)

    override fun Vector2D.distanceIdeal(other: Vector2D): Double =
        SphericalUtil.computeDistanceBetween(LatLng(x, y), LatLng(other.x, other.y))

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