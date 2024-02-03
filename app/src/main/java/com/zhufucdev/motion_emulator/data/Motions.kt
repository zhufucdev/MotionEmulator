package com.zhufucdev.motion_emulator.data

import com.zhufucdev.me.stub.Motion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object Motions : DataStore<Motion>() {
    override val typeName: String get() = "motion"
    override val clazz: KClass<Motion> = Motion::class
    override val dataSerializer: KSerializer<Motion> get() = serializer()
}