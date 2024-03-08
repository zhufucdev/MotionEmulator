package com.zhufucdev.motion_emulator.extension

import android.hardware.Sensor
import com.zhufucdev.me.stub.Motion

fun Motion.estimateSpeed(): Double? {
    fun containsType(type: Int) = timelines.containsKey(type)
    val counter = containsType(Sensor.TYPE_STEP_COUNTER)
    val detector = containsType(Sensor.TYPE_STEP_DETECTOR)
    return null //TODO reimplement
}
