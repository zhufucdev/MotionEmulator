package com.zhufucdev.motion_emulator

import android.content.Context
import android.location.Geocoder
import com.zhufucdev.motion_emulator.data.Vector2D
import com.google.android.gms.maps.model.LatLng
import com.zhufucdev.motion_emulator.data.Point
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException

fun Vector2D.toGoogleLatLng() = LatLng(x, y)

fun LatLng.toPoint() = Point(latitude, longitude)

suspend fun getAddressWithGoogle(target: LatLng, context: Context): String? {
    return coroutineScope {
        async {
            try {
                val results = Geocoder(context).getFromLocation(target.latitude, target.longitude, 1)
                    ?: return@async null

                results[0].subLocality ?: results[0].locality
            } catch (e: IOException) {
                null
            }
        }.await()
    }
}