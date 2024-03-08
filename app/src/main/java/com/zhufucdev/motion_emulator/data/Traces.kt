package com.zhufucdev.motion_emulator.data

import com.zhufucdev.me.stub.Trace
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

object Traces : DataStore<Trace>() {
    override val typeName: String get() = "trace"
    override val clazz: KClass<Trace> = Trace::class
    override val dataSerializer: KSerializer<Trace> = serializer()
}