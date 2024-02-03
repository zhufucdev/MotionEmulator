package com.zhufucdev.motion_emulator.data

import com.zhufucdev.motion_emulator.ui.model.EmulationRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

object Emulations : DataStore<EmulationRef>(){
    override val typeName: String = "emulation"
    override val dataSerializer: KSerializer<EmulationRef> = serializer()
}
