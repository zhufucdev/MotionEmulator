package com.zhufucdev.motion_emulator.ui.map

import com.zhufucdev.motion_emulator.data.CoordinateSystem
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.toAmapLatLng
import com.zhufucdev.motion_emulator.toGoogleLatLng
import com.zhufucdev.motion_emulator.toPoint

data class TraceBounds(val northeast: Point, val southwest: Point)

fun TraceBounds(trace: Trace): TraceBounds {
    return if (trace.coordinateSystem == CoordinateSystem.WGS84) {
        val builder = com.google.android.gms.maps.model.LatLngBounds.builder()
        trace.points.forEach {
            builder.include(it.toGoogleLatLng())
        }
        val result = builder.build()
        TraceBounds(result.northeast.toPoint(), result.southwest.toPoint())
    } else {
        val builder = com.amap.api.maps.model.LatLngBounds.builder()
        trace.points.forEach {
            builder.include(it.toAmapLatLng())
        }
        val result = builder.build()
        TraceBounds(result.northeast.toPoint(), result.southwest.toPoint())
    }
}

fun TraceBounds.amap() =
    com.amap.api.maps.model.LatLngBounds(southwest.toAmapLatLng(), northeast.toAmapLatLng())
fun TraceBounds.google() =
    com.google.android.gms.maps.model.LatLngBounds(southwest.toGoogleLatLng(), northeast.toGoogleLatLng())
