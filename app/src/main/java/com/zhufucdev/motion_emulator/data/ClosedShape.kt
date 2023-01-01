package com.zhufucdev.motion_emulator.data

/**
 * Array of [Vector2D]s, forming a closed shape
 * on a plane or something similar, like the earth.
 */
interface ClosedShape {
    val points: List<Vector2D>
}