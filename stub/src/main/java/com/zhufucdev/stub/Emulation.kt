package com.zhufucdev.stub

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

@Serializable(EmulationSerializer::class)
data class Emulation(
    val trace: Trace,
    val motion: Box<Motion>,
    val cells: Box<CellTimeline>,
    val velocity: Double,
    val repeat: Int,
    val satelliteCount: Int
)

class EmulationSerializer : KSerializer<Emulation> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.zhufucdev.motion_emulator.data.Emulation") {
            element("trace", serialDescriptor<Trace>())
            element("motion", serialDescriptor<String>())
            element("cells", serialDescriptor<String>())
            element("velocity", serialDescriptor<Double>())
            element("repeat", serialDescriptor<Int>())
            element("satellites", serialDescriptor<Int>())
        }

    override fun deserialize(decoder: Decoder): Emulation = decoder.decodeStructure(descriptor) {
        var trace: Trace? = null
        var motion: Box<Motion> = EmptyBox()
        var cells: Box<CellTimeline> = EmptyBox()
        var velocity: Double? = null
        var repeat: Int? = null
        var satellites: Int? = null

        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> trace = decodeSerializableElement(descriptor, index, serializer())
                1 -> motion =
                    Box.decodeFromString(decodeStringElement(descriptor, index))

                2 -> cells =
                    Box.decodeFromString(decodeStringElement(descriptor, index))

                3 -> velocity = decodeDoubleElement(descriptor, index)
                4 -> repeat = decodeIntElement(descriptor, index)
                5 -> satellites = decodeIntElement(descriptor, index)
            }
        }

        requireNotNull(trace)
        requireNotNull(velocity)
        requireNotNull(repeat)
        requireNotNull(satellites)

        Emulation(trace, motion, cells, velocity, repeat, satellites)
    }

    override fun serialize(encoder: Encoder, value: Emulation) =
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(descriptor, 0, serializer(), value.trace)
            encodeStringElement(descriptor, 1, value.motion.encodeToString())
            encodeStringElement(descriptor, 2, value.cells.encodeToString())
            encodeDoubleElement(descriptor, 3, value.velocity)
            encodeIntElement(descriptor, 4, value.repeat)
            encodeIntElement(descriptor, 5, value.satelliteCount)
        }
}
