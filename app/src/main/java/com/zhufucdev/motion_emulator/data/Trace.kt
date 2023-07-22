package com.zhufucdev.motion_emulator.data

import com.zhufucdev.stub.Trace
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

object Traces : DataStore<Trace>() {
    override val typeName: String get() = "record"
    override val dataSerializer: KSerializer<Trace> get() = serializer()
}