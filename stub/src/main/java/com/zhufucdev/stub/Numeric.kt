package com.zhufucdev.stub

import kotlin.math.cos
import kotlin.math.sin
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
open class Vector2D(val x: Double, val y: Double) {
    operator fun plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
    operator fun minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)

    operator fun times(matrix: Matrix2x2) = Vector2D(
        this.x * matrix[0, 0] + this.y * matrix[1, 0],
        this.x * matrix[0, 1] + this.y * matrix[1, 1]
    )

    operator fun times(factor: Double) = Vector2D(x * factor, y * factor)

    override fun toString(): String = "(x=$x, y=$y)"

    override fun equals(other: Any?): Boolean {
        if (other !is Vector2D) return false
        return other.x == x && other.y == y
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        return result
    }

    companion object {
        val zero get() = Vector2D(0.0, 0.0)
        val one get() = Vector2D(1.0, 1.0)
    }
}

@Serializable
data class Matrix2x2(val values: DoubleArray = DoubleArray(4)) {
    operator fun get(row: Int, column: Int): Double {
        if (row <= 1 && column <= 1) {
            return values[row * 2 + column]
        } else if (row > 1) {
            dimensionError(row)
        } else {
            dimensionError(column)
        }
    }

    operator fun times(other: Matrix2x2) =
        Matrix2x2(
            doubleArrayOf(
                this[0, 0] * other[0, 0] + this[0, 1] * other[1, 0],
                this[0, 0] * other[0, 1] + this[0, 1] * other[1, 1],
                this[1, 0] * other[0, 0] + this[1, 1] * other[1, 0],
                this[1, 0] * other[0, 1] + this[1, 1] * other[1, 1]
            )
        )

    operator fun times(vector: Vector2D) =
        Vector2D(
            this[0, 0] * vector.x + this[1, 0] * vector.y,
            this[0, 1] * vector.x + this[1, 1] * vector.y
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Matrix2x2

        if (!values.contentEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        return values.contentHashCode()
    }

    companion object {
        fun rotate(rad: Double): Matrix2x2 {
            val sin = sin(rad)
            val cos = cos(rad)
            return Matrix2x2(
                doubleArrayOf(
                    cos, sin, -sin, cos
                )
            )
        }

        fun scale(x: Double, y: Double): Matrix2x2 {
            return Matrix2x2(
                doubleArrayOf(
                    x, 0.0, 0.0, y
                )
            )
        }

        val eye get() = Matrix2x2(doubleArrayOf(1.0, 0.0, 0.0, 1.0))
    }
}

/**
 * Array of [Vector2D]s, forming a closed shape
 * on a plane or something similar, like the earth.
 */
interface ClosedShape {
    val points: List<Vector2D>
}

private fun dimensionError(dimension: Int): Nothing =
    throw IllegalArgumentException("Dimension error (targeting ${dimension}D)")

fun Vector2D.lenTo(other: Vector2D): Double =
    sqrt((x - other.x).pow(2) + (y - other.y).pow(2))

fun Vector2D.toPoint(coordinateSystem: CoordinateSystem = CoordinateSystem.GCJ02) = Point(x, y, coordinateSystem)

/**
 * Get the geometric center of a [ClosedShape].
 *
 * @param projector The calculation will happen on the **ideal**
 * plane. Basically, a point is projected to the ideal plane, then
 * back to target plane.
 */
fun ClosedShape.center(projector: Projector = BypassProjector): Vector2D {
    if (points.isEmpty()) throw IllegalArgumentException("points is empty")

    var sum = points.first()
    for (i in 1 until points.size) sum += with(projector) { points[i].toIdeal() }

    return with(projector) { Vector2D(sum.x / points.size, sum.y / points.size).toTarget() }
}

/**
 * Get circumference of a [ClosedShape].
 *
 * @param projector The calculation will happen on the **target** plane.
 */
fun ClosedShape.circumference(projector: Projector = BypassProjector): Double {
    var c = 0.0
    if (points.isEmpty()) return c
    for (i in 0 until points.lastIndex - 1) {
        c += with(projector) { points[i].distance(points[i + 1]) }
    }
    if (points.size > 2) c += with(projector) { points[0].distance(points.last()) }
    return c
}

/**
 * Treat the array as a vector and perform subtraction
 *
 * Sizes must be the same
 */
operator fun FloatArray.minus(other: FloatArray): FloatArray {
    return mapIndexed { index, fl ->
        fl - other[index]
    }.toFloatArray()
}

/**
 * Treat the array as a vector and perform addition
 *
 * Sizes must be the same
 */
operator fun FloatArray.plus(other: FloatArray): FloatArray {
    return mapIndexed { index, fl ->
        fl + other[index]
    }.toFloatArray()
}

/**
 * Treat the array as a vector and perform multiplication
 *
 * Sizes must be the same
 */
operator fun FloatArray.times(other: Float): FloatArray {
    return map { fl ->
        fl * other
    }.toFloatArray()
}
