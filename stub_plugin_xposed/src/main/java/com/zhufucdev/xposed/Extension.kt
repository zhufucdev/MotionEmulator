package com.zhufucdev.xposed

fun ClassLoader.loadAMapLocation(): Class<*> {
    return loadClass("com.amap.api.location.AMapLocation")
}

fun ClassLoader.loadAMapListener(): Class<*> {
    return loadClass("com.amap.api.location.AMapLocationListener")
}

fun ClassLoader.loadAMapLocationClient(): Class<*> {
    return loadClass("com.amap.api.location.AMapLocationClient")
}
