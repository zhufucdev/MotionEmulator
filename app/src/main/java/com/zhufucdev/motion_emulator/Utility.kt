package com.zhufucdev.motion_emulator

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.geometry.Offset
import androidx.navigation.NavController
import com.amap.api.maps.AMap
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.hook_frontend.Emulation
import com.zhufucdev.motion_emulator.hook_frontend.EmulationRef
import io.ktor.client.*
import io.ktor.client.call.body
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
        repeat(n) {
            append("#")
        }
    })
    df.roundingMode = RoundingMode.HALF_UP
    return df.format(this)
}

fun Float.toFixed(n: Int): String {
    val df = DecimalFormat(buildString {
        append("#.")
        repeat(n) {
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
fun Vector2D.toLatLng(): LatLng = LatLng(x, y)
fun LatLng.toPoint(): Point = Point(latitude, longitude)

fun skipAmapFuckingLicense(context: Context) {
    MapsInitializer.updatePrivacyShow(context, true, true)
    MapsInitializer.updatePrivacyAgree(context, true)
}

fun Vector2D.lenTo(other: Vector2D): Double =
    sqrt((x - other.x).pow(2) + (y - other.y).pow(2))

fun Vector2D.toPoint() = Point(x, y)

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

fun <T : Data> Box<T>.ref() =
    when (this) {
        is EmptyBox<T> -> EMPTY_REF
        is BlockBox<T> -> BLOCK_REF
        else -> value?.id ?: NULL_REF
    }

fun Vector2D.toOffset() = Offset(x.toFloat(), y.toFloat())
fun Offset.toVector2d() = Vector2D(x * 1.0, y * 1.0)

/**
 * To involve [MutableList.add], but avoid [IndexOutOfBoundsException]
 */
fun <T> MutableList<T>.insert(index: Int, element: T) {
    if (index >= size) {
        add(element)
    } else {
        add(index, element)
    }
}
