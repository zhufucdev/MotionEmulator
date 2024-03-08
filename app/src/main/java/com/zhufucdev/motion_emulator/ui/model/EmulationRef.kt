package com.zhufucdev.motion_emulator.ui.model

import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.extension.StoredBox
import kotlinx.serialization.Serializable

@Serializable
data class EmulationRef(
    override val id: String,
    val name: String,
    val trace: String,
    val motion: String,
    val cells: String,
    val velocity: Double,
    val repeat: Int,
    val satelliteCount: Int,
) : Data

fun EmulationRef.emulation() = Emulation(
    trace = StoredBox(trace, Traces),
    motion = StoredBox(motion, Motions),
    cells = StoredBox(cells, Telephonies),
    repeat = repeat,
    velocity = velocity,
    satelliteCount = satelliteCount
)