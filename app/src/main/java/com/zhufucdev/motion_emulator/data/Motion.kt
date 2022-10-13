package com.zhufucdev.motion_emulator.data

import kotlinx.serialization.Serializable

/**
 * Basic motion record unit
 *
 * @param data SensorType to its value
 * @param elapsed Time from start (in sec.)
 */
@Serializable
data class Moment(val elapsed: Float, val data: MutableMap<Int, FloatArray>)

/**
 * Motion record, composed with series of [Moment]s.
 * @param time Time when it was recorded in millis.
 * @param moments The series.
 */
@Serializable
data class Motion(val time: Long, val moments: List<Moment>)