package com.zhufucdev.motion_emulator.extension

import android.location.Location
import com.zhufucdev.me.stub.CoordinateSystem
import com.zhufucdev.me.stub.Point

/**
 * Returns the corresponding instance of [Point], whose
 * coordination system is WGS84 of course.
 */
fun Location.toPoint(): Point = Point(latitude, longitude, CoordinateSystem.WGS84)
