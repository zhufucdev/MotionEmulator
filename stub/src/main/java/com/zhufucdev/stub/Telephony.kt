package com.zhufucdev.stub

import android.annotation.SuppressLint
import android.os.*
import android.os.Parcelable.Creator
import android.telephony.*
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import java.text.DateFormat

/**
 * A snapshot taken from [TelephonyManager]
 */
@Serializable(CellSerializer::class)
data class CellMoment(
    override val elapsed: Float,
    val cell: List<CellInfo> = emptyList(),
    val neighboring: List<NeighboringCellInfo> = emptyList(),
    val location: CellLocation? = null
) : Moment

@Serializable
data class CellTimeline(
    override val id: String,
    val name: String? = null,
    val time: Long,
    val moments: List<CellMoment>
) : Data {
    override fun getDisplayName(format: DateFormat): String =
        name.takeIf { !it.isNullOrEmpty() } ?: format.dateString(time)

    @OptIn(ExperimentalSerializationApi::class)
    override fun writeTo(stream: OutputStream) {
        Json.encodeToStream(kotlinx.serialization.serializer(), this, stream)
    }
}

class CellSerializer : KSerializer<CellMoment> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("$SERIALIZATION_ID.data.Cell") {
            val balsd = ByteArrayListSerializer().descriptor
            element("elapsed", serialDescriptor<Float>())
            element("cell", balsd)
            element("neighboring", balsd)
            element("location", balsd)
        }

    override fun deserialize(decoder: Decoder): CellMoment = decoder.decodeStructure(descriptor) {
        var elapsed = -1F
        var cells = emptyList<CellInfo>()
        var neighboring = emptyList<NeighboringCellInfo>()
        var location: CellLocation? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> elapsed = decodeFloatElement(descriptor, index)
                1 -> cells = decodeParcelableListElement(descriptor, CellInfo.CREATOR, index)
                2 -> neighboring =
                    decodeParcelableListElement(descriptor, NeighboringCellInfo.CREATOR, index)
                3 -> location = decodeCellLocationElement(descriptor, index)

                else -> throw SerializationException("Unexpected index $index")
            }
        }
        if (elapsed == -1F || cells.isEmpty() && neighboring.isEmpty() && location == null) {
            error("Missing field (elapsed = $elapsed, neighboring.empty = ${neighboring.isEmpty()}, location = $location)")
        }
        CellMoment(elapsed, cells, neighboring, location)
    }

    override fun serialize(encoder: Encoder, value: CellMoment) =
        encoder.encodeStructure(descriptor) {
            encodeFloatElement(descriptor, 0, value.elapsed)
            encodeParcelableListElement(descriptor, 1, value.cell)
            if (value.neighboring.isNotEmpty())
                encodeParcelableListElement(descriptor, 2, value.neighboring)
            if (value.location != null)
                encodeCellLocationElement(descriptor, 3, value.location)
        }
}

@Suppress("FunctionName")
fun ByteArrayListSerializer() = ListSerializer(ByteArraySerializer())

fun CompositeEncoder.encodeParcelableListElement(
    descriptor: SerialDescriptor,
    index: Int,
    value: List<Parcelable>
) {
    encodeSerializableElement( // cell info
        descriptor, index, ByteArrayListSerializer(),
        value.map { p ->
            Parcel.obtain().use {
                p.writeToParcel(it, 0)
                it.marshall()
            }
        }
    )
}

fun CompositeEncoder.encodeCellLocationElement(
    descriptor: SerialDescriptor,
    index: Int,
    value: CellLocation
) {
    encodeSerializableElement(
        descriptor, index, ByteArraySerializer(),
        Parcel.obtain().use {
            val bundle = Bundle()
            when (value) {
                is CdmaCellLocation -> {
                    bundle.putByte("type", 0)
                    value.fillInNotifierBundle(bundle)
                }
                is GsmCellLocation -> {
                    bundle.putByte("type", 1)
                    value.fillInNotifierBundle(bundle)
                }
                else -> throw NotImplementedError("while encoding cell location (${value.javaClass.simpleName})")
            }
            bundle.writeToParcel(it, 0)
            it.marshall()
        }
    )
}

fun <T : Parcelable> CompositeDecoder.decodeParcelableListElement(
    descriptor: SerialDescriptor,
    creator: Creator<T>,
    index: Int
): List<T> {
    val serializer = ListSerializer(ByteArraySerializer())
    val eleEncoded = decodeSerializableElement(descriptor, index, serializer)
    val ele = arrayListOf<T>()
    eleEncoded.forEach { c ->
        Parcel.obtain().use {
            it.unmarshall(c, 0, c.size)
            it.setDataPosition(0)
            ele.add(creator.createFromParcel(it))
        }
    }
    return ele
}

@SuppressLint("ParcelClassLoader")
fun CompositeDecoder.decodeCellLocationElement(
    descriptor: SerialDescriptor,
    index: Int
): CellLocation {
    val byteArray = decodeSerializableElement(descriptor, index, ByteArraySerializer())
    return Parcel.obtain().use {
        it.unmarshall(byteArray, 0, byteArray.size)
        val bundle = it.readBundle() ?: throw CellLocationDecodeException("bundle unavailable")
        when (val t = bundle.getByte("type")) {
            0.toByte() -> {
                CdmaCellLocation(bundle)
            }
            1.toByte() -> {
                GsmCellLocation(bundle)
            }
            else -> throw CellLocationDecodeException("unknown type: $t")
        }
    }
}

class CellLocationDecodeException(override val message: String) : Exception()

fun <T> Parcel.use(block: (Parcel) -> T): T {
    val result = block(this)
    recycle()
    return result
}