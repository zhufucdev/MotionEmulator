package com.zhufucdev.motion_emulator.data

import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.me.stub.MotionTimeline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object Motions : DataStore<Motion>() {
    override val typeName: String get() = "motion"
    override val clazz: KClass<Motion> = Motion::class
    override val dataSerializer: KSerializer<Motion> = serializer()
}

object MotionComposites : DataStore<MotionComposite>() {
    override val typeName: String get() = "motion_composite"
    override val clazz: KClass<MotionComposite> get() = MotionComposite::class
    override val dataSerializer: KSerializer<MotionComposite> get() = serializer()
}

@Serializable
data class MotionComposite(
    override val id: String,
    val name: String,
    private val ref: List<String>
) : Data {
    val timelines by lazy { ref.map { Motions[it] } }
}