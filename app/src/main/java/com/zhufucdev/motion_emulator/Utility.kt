package com.zhufucdev.motion_emulator

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.amap.api.maps.model.LatLng
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*

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
