package com.zhufucdev.motion_emulator

import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.data.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Test

import org.junit.Assert.*
import org.junit.Before
import kotlin.math.sqrt

class SaltUnitTest {
    private val salts = mutableListOf<Salt2dRuntime>()
    private val trace =
        Trace(
            id = NanoIdUtils.randomNanoId(),
            name ="testificate trace",
            listOf(
                Point(-1.0, -1.0),
                Point(-1.0, 1.0),
                Point(1.0, 1.0),
                Point(1.0, -1.0)
            )
        )

    @Before
    fun init() {
        salts.add(
            Json.decodeFromString(
                serializer<Salt2dData>(),
                """
                    {
                        "id": "mAA1-o_maQO6LEEoApO9-",
                        "elements": [
                          {
                            "values": [
                              "centerX",
                              "centerY"
                            ],
                            "type": "Anchor"
                          },
                          {
                            "values": [
                              "10",
                              "1"
                            ],
                            "type": "Scale"
                          },
                          {
                            "values": [
                              "centerX",
                              "centerY"
                            ],
                            "type": "Anchor"
                          },
                          {
                            "values": [
                              "1",
                              "0"
                            ],
                            "type": "Translation"
                          }
                        ]
                    }
        """
            ).runtime()
        )

        salts.add(
            Json.decodeFromString(
                serializer<Salt2dData>(),
                """
                {
                    "id": "cxvwOuzEtZn5TPpoDBc8r",
                    "elements": [
                      {
                        "values": [
                          "centerX",
                          "centerY"
                        ],
                        "type": "Anchor"
                      },
                      {
                        "values": [
                          "2pi*x",
                          "1"
                        ],
                        "type": "Rotation"
                      }
                    ],
                    "factors": [
                      {
                        "id": "rCWv04jqn-V25ehC1QE4q",
                        "name": "x",
                        "distribution": [
                          0.013320762980579678,
                          0.7749852671986937,
                          0.957235394883454,
                          0.38061790932495787
                        ]
                      }
                    ]
                  }
            """
            ).runtime()
        )
    }

    @Test
    fun transformers_parsing_correct() {
        val resolution = salts[0].resolve(MapProjector, trace)
        assertEquals("size not match", 2, resolution.size)
        assertEquals("size of transforms not match", 1, resolution[0].transforms.size)
        assertEquals("type of transforms not match", TransformationChain::class, resolution[0].transforms[0]::class)
        // TODO: complete this shit
    }

    @Test
    fun center_correct() {
        val resolution = salts[0].resolve(MapProjector, trace)
        assertEquals("anchor not match", Vector2D.zero, resolution[1].anchor)
    }

    @Test
    fun transform1_correct() {
        val testPoint = Vector2D.one
        val transformed = salts[0].apply(point = testPoint, parent = trace)
        assertEquals("x not match", 11.0, transformed.x, 1e-6)
        assertEquals("y not match", 1.0, transformed.y, 1e-6)
    }

    @Test
    fun transform2_correct() {
        val testPoint = Vector2D.one
        val transformed = salts[1].apply(point = testPoint, parent = trace)
        assertEquals("not in radius", sqrt(2.0), transformed.lenTo(Vector2D.zero), 1e-6)
    }
}