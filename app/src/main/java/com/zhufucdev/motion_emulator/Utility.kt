package com.zhufucdev.motion_emulator

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.NavController
import com.amap.api.maps.AMap
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.hook_frontend.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.EmulationRef
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

fun getAttrColor(@AttrRes id: Int, context: Context): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(id, typedValue, true)
    return typedValue.data
}

private val ktorClient = HttpClient(Android) {
    install(ContentNegotiation) {
        json()
    }
    install(HttpTimeout) {
        val timeout = 10000L
        connectTimeoutMillis = timeout
        socketTimeoutMillis = timeout
        requestTimeoutMillis = timeout
    }

    defaultRequest {
        accept(ContentType.Application.Json)
    }
}

/**
 * Get a human-readable address of PoI
 *
 * This is in Mandarin, which sucks
 */
suspend fun getAddress(location: LatLng): String? {
    val req = ktorClient.get("https://restapi.amap.com/v3/geocode/regeo") {
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

fun Double.toFixed(n: Int): String {
    val df = DecimalFormat(buildString {
        append("#.")
        for (i in 0..n) {
            append("#")
        }
    })
    df.roundingMode = RoundingMode.HALF_UP
    return df.format(this)
}

fun dateString(time: Long = System.currentTimeMillis()): String =
    SimpleDateFormat.getDateTimeInstance().format(Date(time))

fun isDarkModeEnabled(resources: Resources) =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * Treat the array as a 3D vector
 * @return its length
 */
fun FloatArray.length(): Float {
    return sqrt(first().pow(2) + get(1).pow(2) + get(2).pow(2))
}

/**
 * To remove gravity from a 3D vector
 */
fun FloatArray.filterHighPass(): FloatArray {
    val gravity = FloatArray(3)
    val linearAcceleration = FloatArray(3)
    val alpha = 0.8f

    // Isolate the force of gravity with the low-pass filter.
    gravity[0] = alpha * gravity[0] + (1 - alpha) * this[0]
    gravity[1] = alpha * gravity[1] + (1 - alpha) * this[1]
    gravity[2] = alpha * gravity[2] + (1 - alpha) * this[2]

    // Remove the gravity contribution with the high-pass filter.
    linearAcceleration[0] = this[0] - gravity[0]
    linearAcceleration[1] = this[1] - gravity[1]
    linearAcceleration[2] = this[2] - gravity[2]
    return linearAcceleration
}

/**
 * Treat the array as a vector and perform subtraction
 *
 * Sizes must be the same
 */
operator fun FloatArray.minus(other: FloatArray): FloatArray {
    return mapIndexed { index, fl ->
        fl - other[index]
    }.toFloatArray()
}

/**
 * Treat the array as a vector and perform addition
 *
 * Sizes must be the same
 */
operator fun FloatArray.plus(other: FloatArray): FloatArray {
    return mapIndexed { index, fl ->
        fl + other[index]
    }.toFloatArray()
}

/**
 * Treat the array as a vector and perform multiplication
 *
 * Sizes must be the same
 */
operator fun FloatArray.times(other: Float): FloatArray {
    return map { fl ->
        fl * other
    }.toFloatArray()
}

fun Point.toLatLng(): LatLng = LatLng(latitude, longitude)
fun LatLng.toPoint(): Point = Point(latitude, longitude)

fun skipAmapFuckingLicense(context: Context) {
    MapsInitializer.updatePrivacyShow(context, true, true)
    MapsInitializer.updatePrivacyAgree(context, true)
}

/**
 * Treat [Point] as a 2d vector and calculate
 * the length
 */
fun Point.lenTo(other: Point): Double =
    sqrt((latitude - other.latitude).pow(2) + (longitude - other.longitude).pow(2))

fun AMap.unifyTheme(resources: Resources) {
    mapType = if (isDarkModeEnabled(resources)) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
}

val ApplicationInfo.isSystemApp get() = flags and ApplicationInfo.FLAG_SYSTEM != 0

/**
 * Get the [LatLngBounds] from a trace
 */
fun List<Point>.bounds(): LatLngBounds =
    LatLngBounds
        .builder()
        .apply {
            forEach { include(it.toLatLng()) }
        }
        .build()

/**
 * Do minus operation, treating
 * the two [LatLng]s as 2D vectors
 */
operator fun LatLng.minus(other: LatLng) =
    LatLng(latitude - other.latitude, longitude - other.longitude)

object MapFixUtil {
    private const val pi = 3.14159265358979324
    private const val a = 6378245.0
    private const val ee = 0.00669342162296594323
    fun transform(wgLat: Double, wgLon: Double): DoubleArray {
        val latlng = DoubleArray(2)
        if (outOfChina(wgLat, wgLon)) {
            latlng[0] = wgLat
            latlng[1] = wgLon
            return latlng
        }
        var dLat = transformLat(wgLon - 105.0, wgLat - 35.0)
        var dLon = transformLon(wgLon - 105.0, wgLat - 35.0)
        val radLat = wgLat / 180.0 * pi
        var magic = sin(radLat)
        magic = 1 - ee * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtMagic) * pi)
        dLon = dLon * 180.0 / (a / sqrtMagic * cos(radLat) * pi)
        latlng[0] = wgLat - dLat
        latlng[1] = wgLon - dLon
        return latlng
    }

    private fun outOfChina(lat: Double, lon: Double): Boolean {
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
}

fun AppCompatActivity.initializeToolbar(
    toolbar: Toolbar,
    navController: NavController? = null
) {
    setSupportActionBar(toolbar)
    toolbar.setNavigationOnClickListener {
        if (navController?.navigateUp() != true) {
            finish()
        }
    }
}

fun Emulation.ref() =
    EmulationRef(trace.id, motion.ref(), cells.ref(), velocity, repeat, satelliteCount)

fun <T : Referable> Box<T>.ref() =
    when(this) {
        is EmptyBox<T> -> EMPTY_REF
        is BlockBox<T> -> BLOCK_REF
        else -> value?.id ?: NULL_REF
    }
