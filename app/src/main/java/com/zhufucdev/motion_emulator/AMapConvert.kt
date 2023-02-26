package com.zhufucdev.motion_emulator

import android.content.Context
import android.content.res.Resources
import com.amap.api.maps.AMap
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.zhufucdev.motion_emulator.data.Point
import com.zhufucdev.motion_emulator.data.Vector2D
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Vector2D.toAmapLatLng(): LatLng = LatLng(x, y)

fun LatLng.toPoint(): Point = Point(latitude, longitude)

fun LatLonPoint.toPoint(): Point = Point(latitude, longitude)

fun AMap.unifyTheme(resources: Resources) {
    mapType = if (isDarkModeEnabled(resources)) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
}

fun skipAmapFuckingLicense(context: Context) {
    MapsInitializer.updatePrivacyShow(context, true, true)
    MapsInitializer.updatePrivacyAgree(context, true)
}

/**
 * Do minus operation, treating
 * the two [LatLng]s as 2D vectors
 */
operator fun LatLng.minus(other: LatLng) =
    LatLng(latitude - other.latitude, longitude - other.longitude)

/**
 * Get a human-readable address of PoI
 *
 * This is in Mandarin, which sucks
 */
suspend fun getAddressWithAmap(location: LatLng): String? {
    val req = defaultKtorClient.get("https://restapi.amap.com/v3/geocode/regeo") {
        parameter("key", BuildConfig.AMAP_WEB_KEY)
        parameter("location", "${location.longitude.toFixed(6)},${location.latitude.toFixed(6)}")
    }
    if (!req.status.isSuccess()) return null
    val res = req.body<JsonObject>()
    if (res["status"]?.jsonPrimitive?.int != 1
        || res["info"]?.jsonPrimitive?.content != "OK"
    ) return null
    return res["regeocode"]!!.jsonObject["formatted_address"]!!.jsonPrimitive.content
}
