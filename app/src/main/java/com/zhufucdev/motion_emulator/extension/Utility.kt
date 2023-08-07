package com.zhufucdev.motion_emulator.extension

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.hardware.Sensor
import android.location.Location
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.ui.geometry.Offset
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.preference.PreferenceManager
import com.google.android.material.appbar.AppBarLayout
import com.zhufucdev.stub.*
import com.zhufucdev.stub.Emulation
import com.zhufucdev.motion_emulator.provider.EmulationRef
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun getAttrColor(@AttrRes id: Int, context: Context): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(id, typedValue, true)
    return typedValue.data
}

val defaultKtorClient = HttpClient(OkHttp) {
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

fun DateFormat.dateString(time: Long = System.currentTimeMillis()): String =
    format(Date(time))

fun SharedPreferences.effectiveTimeFormat(): DateFormat {
    val useCustom = getBoolean("customize_time_format", false)
    return if (useCustom) {
        val format = getString("time_format", "dd-MM-yyyy hh:mm:ss")
        SimpleDateFormat(format, Locale.getDefault())
    } else {
        SimpleDateFormat.getDateTimeInstance()
    }
}

fun Motion.estimateSpeed(): Double? {
    fun containsType(type: Int) = sensorsInvolved.contains(type)
    val counter = containsType(Sensor.TYPE_STEP_COUNTER)
    val detector = containsType(Sensor.TYPE_STEP_DETECTOR)
    if (!counter && !detector)
        return null //TODO use more sensor types

    var lastMoment: MotionMoment? = null
    var sum = 0.0
    var count = 0
    if (counter) {
        // if counter is available, drop detector
        for (current in moments) {
            if (current.data.containsKey(Sensor.TYPE_STEP_COUNTER)) {
                val last = lastMoment
                if (last == null) {
                    lastMoment = current
                    continue
                }
                val steps =
                    current.data[Sensor.TYPE_STEP_COUNTER]!!.first() - last.data[Sensor.TYPE_STEP_COUNTER]!!.first()
                val time = current.elapsed - last.elapsed
                sum += 1.2 * steps / time
                count++
            }
        }
    } else {
        // if not, relay on detector
        for (current in moments) {
            if (current.data.containsKey(Sensor.TYPE_STEP_DETECTOR)) {
                val last = lastMoment
                if (last == null) {
                    lastMoment = current
                    continue
                }
                val time = current.elapsed - last.elapsed
                sum += 1.2 / time
                count++
            }
        }
    }

    if (sum < 0 || sum.isNaN() || count <= 0) return null
    return sum / count
}

fun Motion.estimateTimespan(): Duration {
    if (moments.size < 2) return 0.seconds
    return (moments.last().elapsed - moments.first().elapsed * 1.0).seconds
}

fun Context.effectiveTimeFormat(): DateFormat {
    val preferences by lazySharedPreferences()
    return preferences.effectiveTimeFormat()
}

fun isDarkModeEnabled(resources: Resources) =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

/**
 * Returns the corresponding instance of [Point], whose
 * coordination system is WGS84 of course.
 */
fun Location.toPoint(): Point = Point(latitude, longitude, CoordinateSystem.WGS84)

val ApplicationInfo.isSystemApp get() = flags and ApplicationInfo.FLAG_SYSTEM != 0

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

fun Activity.setUpStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
}

@SuppressLint("NewApi")
fun Activity.adjustToolbarMarginForNotch(appBarLayout: AppBarLayout) {
    // Notch is only supported by >= Android 9
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val windowInsets = window.decorView.rootWindowInsets
        if (windowInsets != null) {
            val displayCutout = windowInsets.displayCutout
            if (displayCutout != null) {
                val safeInsetTop = displayCutout.safeInsetTop
                appBarLayout.setPadding(0, safeInsetTop, 0, 0)
            }
        }
    }
}

fun Context.sharedPreferences() = PreferenceManager.getDefaultSharedPreferences(this)

fun Context.lazySharedPreferences() = lazy { this.sharedPreferences() }

fun Fragment.lazySharedPreferences() = lazy { requireContext().sharedPreferences() }

