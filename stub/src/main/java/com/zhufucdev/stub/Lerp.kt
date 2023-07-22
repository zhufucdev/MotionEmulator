package com.zhufucdev.stub

/**
 * Result for [ClosedShape.at]
 *
 * @param point the interpolated point
 * @param index the bigger index between which the point was interpolated
 * @param totalLen length of the [ClosedShape] used to interpolate, in target panel's measure
 * @param cache some distance cache humans don't really care
 */
data class ClosedShapeInterp(
    val point: Vector2D,
    val index: Int,
    val totalLen: Double,
    val cache: List<Double>
)

/**
 * Interpolate a point, given [progress] valued
 * between 0 and 1
 *
 * The point may have never been drawn
 *
 * @param from if this algorithm is called many times in an increasing [progress] manner,
 * its last result can be used to help calculate faster
 * @param projector The interpolation will happen on the **ideal** plane. Basically, it's projected to
 * the ideal plane, and back to the target plane
 * @see [ClosedShapeInterp]
 */
fun List<Vector2D>.at(
    progress: Float,
    projector: Projector = BypassProjector,
    from: ClosedShapeInterp? = null
): ClosedShapeInterp {
    if (progress > 1) {
        return ClosedShapeInterp(last(), size - 1, 0.0, emptyList())
    } else if (progress < 0) {
        return ClosedShapeInterp(first(), 0, 0.0, emptyList())
    }

    var totalLen = 0.0
    val cache = if (from == null || from.cache.isEmpty()) {
        buildList {
            add(0.0)
            for (i in 1 until this@at.size) {
                totalLen += with(projector) { this@at[i].distance(this@at[i - 1]) }
                add(totalLen)
            }
        }
    } else {
        totalLen = from.totalLen
        from.cache
    }
    val required = totalLen * progress
    val range = if (from == null) {
        1 until this.size
    } else {
        from.index until this.size
    }
    for (i in range) {
        val current = cache[i]
        if (required == current) {
            return ClosedShapeInterp(this[i], i, totalLen, cache)
        } else if (current > required) {
            val a = with(projector) { this@at[i - 1].toIdeal() }
            val b = with(projector) { this@at[i].toIdeal() }
            val f = (required - cache[i - 1]) / (cache[i] - cache[i - 1])
            return ClosedShapeInterp(
                point = with(projector) {
                    Vector2D(
                        x = (b.x - a.x) * f + a.x,
                        y = (b.y - a.y) * f + a.y
                    ).toTarget()
                },
                index = i,
                totalLen, cache
            )
        }
    }
    return ClosedShapeInterp(this.last(), this.lastIndex, totalLen, cache)
}