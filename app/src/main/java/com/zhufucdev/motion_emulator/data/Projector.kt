package com.zhufucdev.motion_emulator.data

import android.content.Context
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.amap.api.maps.AMapUtils
import com.amap.api.maps.CoordinateConverter
import com.google.maps.android.SphericalUtil
import com.zhufucdev.data.AbstractMapProjector
import com.zhufucdev.data.Projector
import com.zhufucdev.data.Vector2D
import com.zhufucdev.data.lenTo
import com.zhufucdev.motion_emulator.toAmapLatLng
import com.zhufucdev.motion_emulator.toGoogleLatLng
import com.zhufucdev.motion_emulator.ui.manager.FactorCanvas
import kotlin.math.pow
import kotlin.math.sqrt

object MapProjector : AbstractMapProjector() {
    override fun Vector2D.distance(other: Vector2D): Double =
        AMapUtils.calculateLineDistance(this.toAmapLatLng(), other.toAmapLatLng()).toDouble()
    override fun Vector2D.distanceIdeal(other: Vector2D): Double =
        SphericalUtil.computeDistanceBetween(this.toGoogleLatLng(), other.toGoogleLatLng())
}


/**
 * A [Projector] targeting [FactorCanvas]
 */
class CanvasProjector(scope: DrawScope, val boundLeft: Float, val boundBottom: Float) : Projector {
    val drawingSize = with(scope) { Size(size.width - boundLeft, size.height - boundBottom) }

    override fun Vector2D.distance(other: Vector2D): Double =
        sqrt((x - other.x) * drawingSize.width.pow(2) + (y - other.y) * drawingSize.height.pow(2))

    override fun Vector2D.distanceIdeal(other: Vector2D): Double = lenTo(other)

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

    override fun Vector2D.distanceIdeal(other: Vector2D): Double =
        throw UnsupportedOperationException("Projection from AMap is not supported")


    override fun Vector2D.toTarget(): Vector2D =
        cvt.coord(this.toAmapLatLng()).convert().let { Vector2D(it.latitude, it.longitude) }


    override fun Vector2D.toIdeal(): Vector2D =
        throw UnsupportedOperationException("Projection from AMap is not supported")
}