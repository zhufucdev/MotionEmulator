package com.zhufucdev.motion_emulator.data

import com.zhufucdev.me.stub.CellTimeline
import com.zhufucdev.me.stub.Data
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.reflect.KClass


object Telephonies : DataStore<CellTimeline>() {
    override val typeName: String get() = "telephony"
    override val clazz: KClass<CellTimeline> = CellTimeline::class
    override val dataSerializer: KSerializer<CellTimeline> = serializer()
}

object TelephonyComposites : DataStore<TelephonyComposite>() {
    override val typeName: String
        get() = "telephony_composite"
    override val clazz: KClass<TelephonyComposite> get() = TelephonyComposite::class
    override val dataSerializer: KSerializer<TelephonyComposite> = serializer()
}

@Serializable
data class TelephonyComposite(
    override val id: String,
    val name: String,
    private val ref: List<String>
) : Data {
    val timelines by lazy { ref.map { Telephonies[it] } }
}