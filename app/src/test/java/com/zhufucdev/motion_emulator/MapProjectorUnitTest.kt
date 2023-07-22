package com.zhufucdev.motion_emulator

import com.zhufucdev.stub.Vector2D
import com.zhufucdev.motion_emulator.data.MapProjector
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MapProjectorUnitTest {
    val dataset = buildList {
        val base =
            listOf(
                Vector2D(40.0, 120.0),
                Vector2D(26.0, 90.0),
                Vector2D(38.0, 160.0),
                Vector2D(70.0, 40.0)
            )
        repeat(100) {
            val b = base[Random.nextInt(base.size)]
            add(b + Vector2D(Random.nextDouble(), Random.nextDouble()))
        }
    }

    @Test
    fun vector2d_consistency() {
        dataset.forEach {
            assertEquals("vector2d not consistent", it, it)
        }
    }

    @Test
    fun projection_consistency() {
        dataset.forEach {
            val projected = with(MapProjector) { it.toIdeal().toTarget() }
            assertEquals("x not consistent", it.x, projected.x, 3e-5)
            assertEquals("y not consistent", it.y, projected.y, 3e-5)
        }
    }
}