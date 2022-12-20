package com.zhufucdev.motion_emulator.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/**
 * A location on earth.
 */
@Serializable
data class Point(val latitude: Double, val longitude: Double) {
    companion object {
        val zero get() = Point(0.0, 0.0)
    }
}

/**
 * Composed of series of [Point]s.
 * @param name to call the trace
 * @param points to describe the trace's shape and direction
 */
@Serializable
data class Trace(override val id: String, val name: String, val points: List<Point>) : Referable

object Traces : DataStore<Trace>() {
    override val typeName: String get() = "record"
    override val dataSerializer: KSerializer<Trace> get() = serializer()
}