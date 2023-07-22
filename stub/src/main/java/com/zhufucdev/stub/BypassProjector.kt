package com.zhufucdev.stub

import kotlin.math.sqrt

object BypassProjector : Projector {
    override fun Vector2D.distance(other: Vector2D): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    override fun Vector2D.distanceIdeal(other: Vector2D): Double = distance(other)

    override fun Vector2D.toTarget(): Vector2D = this

    override fun Vector2D.toIdeal(): Vector2D = this
}
