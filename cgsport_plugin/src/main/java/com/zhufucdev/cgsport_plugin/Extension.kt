package com.zhufucdev.cgsport_plugin

import com.highcapable.yukihookapi.hook.factory.toClass

val ClassLoader.amapLatLngClass get() = "com.amap.api.maps.model.LatLng".toClass(this)
val ClassLoader.amapLocationClientClass get() = "com.amap.api.location.AMapLocationClient".toClass(this)

fun Any.properties(): Map<String, String> =
    this::class.java.methods.filter { it.name.startsWith("get") && it.parameterCount == 0 }
        .associate { it.name.removePrefix("get") to (it.invoke(this)?.toString() ?: "[null]") }
