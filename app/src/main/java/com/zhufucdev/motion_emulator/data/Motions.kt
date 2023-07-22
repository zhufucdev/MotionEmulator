package com.zhufucdev.motion_emulator.data

import com.zhufucdev.stub.Motion
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

object Motions : DataStore<Motion>() {
    override val typeName: String get() = "motion"
    override val dataSerializer: KSerializer<Motion> get() = serializer()
}