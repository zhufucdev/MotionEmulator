package com.zhufucdev.motion_emulator.data

import kotlinx.serialization.Serializable

@Serializable
data class Point(val latitude: Double, val longitude: Double)

@Serializable
data class Trace(val name: String, val points: List<Point>)