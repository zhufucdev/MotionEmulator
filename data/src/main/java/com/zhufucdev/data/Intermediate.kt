package com.zhufucdev.data

import kotlinx.serialization.Serializable

@Serializable
data class Intermediate(val location: Point, val elapsed: Double, val progress: Float)

@Serializable
data class EmulationInfo(val duration: Double, val length: Double, val owner: String)