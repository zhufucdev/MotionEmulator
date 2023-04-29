package com.zhufucdev.motion_emulator

import android.content.Context
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import com.zhufucdev.motion_emulator.data.CoordinateSystem
import com.zhufucdev.motion_emulator.data.MapProjector
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Vector2D
import java.io.IOException
import kotlin.coroutines.suspendCoroutine

fun Vector2D.toGoogleLatLng() = LatLng(x, y)

fun LatLng.toPoint() = Point(latitude, longitude, CoordinateSystem.WGS84)

suspend fun getAddressWithGoogle(target: LatLng, context: Context): String? =
    suspendCoroutine { res ->
        try {
            val results = Geocoder(context).getFromLocation(target.latitude, target.longitude, 1)
            if (results == null) {
                res.resumeWith(Result.failure(IOException("Unknown")))
            } else {
                res.resumeWith(Result.success(results[0].thoroughfare ?: results[0].featureName))
            }
        } catch (e: IOException) {
            res.resumeWith(Result.failure(e))
        }
    }

fun Point.ensureGoogleCoordinate(): Point =
    if (coordinateSystem == CoordinateSystem.GCJ02 && MapProjector.outOfChina(latitude, longitude))
        with(MapProjector) { toIdeal() }.toPoint(CoordinateSystem.WGS84)
    else this