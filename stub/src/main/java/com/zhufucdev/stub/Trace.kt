package com.zhufucdev.stub

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import java.io.OutputStream
import java.text.DateFormat

/**
 * A location on the Earth. Get it?
 */
@Serializable(PointSerializer::class)
class Point : Vector2D {
    val coordinateSystem: CoordinateSystem

    /**
     * Constructs a [Point], in WGS84 coordinate system
     */
    constructor(latitude: Double, longitude: Double) : super(latitude, longitude) {
        coordinateSystem = CoordinateSystem.WGS84
    }

    /**
     * Constructs a [Point], in a given coordinate system
     */
    constructor(latitude: Double, longitude: Double, coordinateSystem: CoordinateSystem) : super(latitude, longitude) {
        this.coordinateSystem = coordinateSystem
    }

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
@Serializable(TraceSerializer::class)
data class Trace(
    override val id: String,
    val name: String,
    override val points: List<Point>,
    val coordinateSystem: CoordinateSystem = CoordinateSystem.GCJ02,
    val salt: Salt2dData? = null
) : Data, ClosedShape {
    override fun getDisplayName(format: DateFormat): String = name

    @OptIn(ExperimentalSerializationApi::class)
    override fun writeTo(stream: OutputStream) {
        Json.encodeToStream(kotlinx.serialization.serializer(), this, stream)
    }
}

enum class CoordinateSystem {
    /**
     * GCJ-02 is an encrypted coordinate system made by government of China mainland,
     * to keep their map data safe. To learn more, go ahead to
     * [Restrictions on geographic data in China](https://en.wikipedia.org/wiki/Restrictions_on_geographic_data_in_China)
     */
    GCJ02,

    /**
     * WGS-84 is a standard coordinate system, which data can be fetched
     * directly from GPS report. To learn more,
     * [here's wikipedia](https://en.wikipedia.org/wiki/World_Geodetic_System)
     */
    WGS84
}

class TraceSerializer : KSerializer<Trace> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("$SERIALIZATION_ID.data.Trace") {
            element("id", serialDescriptor<Int>())
            element("name", serialDescriptor<String>())
            element("coordSys", serialDescriptor<CoordinateSystem>(), isOptional = true)
            element("points", serialDescriptor<Point>())
            element("salt", serialDescriptor<Salt2dData>(), isOptional = true)
        }

    override fun deserialize(decoder: Decoder): Trace = decoder.decodeStructure(descriptor) {
        var name = ""
        var id = ""
        var coordinateSystem = CoordinateSystem.GCJ02
        var points: List<Point> = emptyList()
        var salt: Salt2dData? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> id = decodeStringElement(descriptor, index)
                1 -> name = decodeStringElement(descriptor, index)
                2 -> coordinateSystem = decodeSerializableElement(descriptor, index, serializer())
                3 -> points = decodeSerializableElement(descriptor, index, serializer())
                4 -> salt = decodeSerializableElement(descriptor, index, serializer<Salt2dData>())
            }
        }

        Trace(id, name, points.map { Point(it.latitude, it.longitude, coordinateSystem) }, coordinateSystem, salt)
    }

    override fun serialize(encoder: Encoder, value: Trace) =
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeStringElement(descriptor, 1, value.name)
            encodeSerializableElement(descriptor, 2, serializer(), value.coordinateSystem)
            encodeSerializableElement(descriptor, 3, serializer(), value.points)
            value.salt?.let {
                encodeSerializableElement(descriptor, 4, serializer(), it)
            }
        }

}

/**
 * **This serializer doesn't come with coordination system support**
 */
class PointSerializer : KSerializer<Point> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("$SERIALIZATION_ID.data.Point") {
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

class PointSerializerCoord : KSerializer<Point> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("$SERIALIZATION_ID.data.Point") {
            element("latitude", serialDescriptor<Double>())
            element("longitude", serialDescriptor<Double>())
            element("coordSys", serialDescriptor<CoordinateSystem>())
        }

    override fun deserialize(decoder: Decoder): Point = decoder.decodeStructure(descriptor) {
        var latitude = 0.0
        var longitude = 0.0
        var coordinateSystem = CoordinateSystem.WGS84
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> latitude = decodeDoubleElement(descriptor, index)
                1 -> longitude = decodeDoubleElement(descriptor, index)
                2 -> coordinateSystem = decodeSerializableElement(descriptor, index, serializer())
            }
        }

        Point(latitude, longitude, coordinateSystem)
    }

    override fun serialize(encoder: Encoder, value: Point) =
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude)
            encodeDoubleElement(descriptor, 1, value.longitude)
            encodeSerializableElement(descriptor, 2, serializer(), value.coordinateSystem)
        }
}