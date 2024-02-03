@file:Suppress("DEPRECATION")

package com.zhufucdev.motion_emulator.data

import com.zhufucdev.me.stub.CellTimeline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlin.reflect.KClass


object Cells : DataStore<CellTimeline>() {
    override val typeName: String get() = "cells"
    override val clazz: KClass<CellTimeline> = CellTimeline::class
    override val dataSerializer: KSerializer<CellTimeline> get() = serializer()
}

