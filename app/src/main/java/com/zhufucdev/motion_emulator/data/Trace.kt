package com.zhufucdev.motion_emulator.data

import com.zhufucdev.motion_emulator.data.BypassProjector.toIdeal
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer

/**
 * A location on the Earth. Get it?
 */
@Serializable(PointSerializer::class)
class Point(latitude: Double, longitude: Double) : Vector2D(latitude, longitude) {
    val latitude get() = x
    val longitude get() = y

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
data class Trace(
    override val id: String,
    val name: String,
    override val points: List<Point>,
    val salt: Salt2dData? = null
) : Referable, ClosedShape {
    val saltedPoints by lazy {
        if (salt != null) {
            val runtime = salt.runtime()
            points.map {
                runtime.apply(
                    point = it,
                    projector = MapProjector,
                    parent = this
                )
            }
        } else {
            points
        }
    }
}

object Traces : DataStore<Trace>() {
    override val typeName: String get() = "record"
    override val dataSerializer: KSerializer<Trace> get() = serializer()
}

class PointSerializer : KSerializer<Point> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.zhufucdev.motion_emulator.data.Point") {
            element("latitude", serialDescriptor<Double>())
            element("longitude", serialDescriptor<Double>())
        }

    override fun deserialize(decoder: Decoder): Point = decoder.decodeStructure(descriptor) {
        var latitude = 0.0
        var longitude = 0.0
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> latitude = decodeDoubleElement(descriptor, index)
                1 -> longitude = decodeDoubleElement(descriptor, index)
            }
        }

        Point(latitude, longitude)
    }

    override fun serialize(encoder: Encoder, value: Point) =
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude)
            encodeDoubleElement(descriptor, 1, value.longitude)
        }
}
