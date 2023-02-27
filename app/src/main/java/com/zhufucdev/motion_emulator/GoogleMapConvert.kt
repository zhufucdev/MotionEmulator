package com.zhufucdev.motion_emulator

import android.content.Context
import android.location.Geocoder
import com.zhufucdev.motion_emulator.data.Vector2D
import com.google.android.gms.maps.model.LatLng
import com.zhufucdev.motion_emulator.data.Point
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import kotlin.coroutines.suspendCoroutine

fun Vector2D.toGoogleLatLng() = LatLng(x, y)

fun LatLng.toPoint() = Point(latitude, longitude)

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