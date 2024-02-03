@file:Suppress("DEPRECATION")

package com.zhufucdev.motion_emulator.data

import com.zhufucdev.me.stub.CellTimeline
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer


object Cells : DataStore<CellTimeline>() {
    override val typeName: String get() = "cells"
    override val dataSerializer: KSerializer<CellTimeline> get() = serializer()
}

