package com.zhufucdev.stub

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.*

/**
 * Snapshot of an emulation process
 *
 * @param location Current location
 * @param elapsed Seconds since current loop
 * @param progress [0, 1] progress of current loop
 */
@Serializable(IntermediateSerializer::class)
data class Intermediate(val location: Point, val elapsed: Double, val progress: Float)

@Serializable
data class EmulationInfo(val duration: Double, val length: Double, val owner: String)

enum class AgentState(val fin: Boolean) {
    NOT_JOINED(false), PENDING(false),
    CANCELED(false), RUNNING(false),
    PAUSED(false), COMPLETED(true),
    FAILURE(true)
}

class IntermediateSerializer : KSerializer<Intermediate> {
    private val pointSerializer by lazy { PointSerializerCoord() }

    override val descriptor: SerialDescriptor by lazy {
        buildClassSerialDescriptor("$SERIALIZATION_ID.Intermediate") {
            element("location", pointSerializer.descriptor)
            element("elapsed", serialDescriptor<Double>())
            element("progress", serialDescriptor<Float>())
        }
    }

    override fun deserialize(decoder: Decoder): Intermediate = decoder.decodeStructure(descriptor) {
        var point: Point? = null
        var elapsed: Double? = null
        var progress: Float? = null
        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> point = decodeSerializableElement(descriptor, index, pointSerializer)
                1 -> elapsed = decodeDoubleElement(descriptor, index)
                2 -> progress = decodeFloatElement(descriptor, index)
            }
        }
        if (point == null || elapsed == null || progress == null) error("corrupt data")
        Intermediate(point, elapsed, progress)
    }

    override fun serialize(encoder: Encoder, value: Intermediate) =
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, pointSerializer, value.location)
            encodeDoubleElement(descriptor, 1, value.elapsed)
            encodeFloatElement(descriptor, 2, value.progress)
        }
}