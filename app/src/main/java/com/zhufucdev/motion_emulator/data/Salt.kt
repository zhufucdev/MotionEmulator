package com.zhufucdev.motion_emulator.data

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateListOf
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.data.SaltElement
import com.zhufucdev.data.SaltType


/**
 * For editor
 */
class MutableSaltElement {
    val id: String
    val values: SnapshotStateList<String>
    val type: SaltType

    constructor(type: SaltType) {
        this.type = type
        id = NanoIdUtils.randomNanoId()
        values =
            when (type) {
                SaltType.Anchor ->
                    mutableStateListOf("centerX", "centerY")

                SaltType.Translation ->
                    mutableStateListOf("0", "0")

                SaltType.Rotation ->
                    mutableStateListOf("0", "0") // "0" for simple mode

                SaltType.Scale ->
                    mutableStateListOf("1", "1")

                SaltType.CustomMatrix ->
                    mutableStateListOf("1", "0", "0", "1")
            }
    }

    constructor(id: String = NanoIdUtils.randomNanoId(), values: List<String>, type: SaltType) {
        this.id = id
        this.values = values.toMutableStateList()
        this.type = type
    }

    operator fun set(dimension: Int, value: String) {
        values[dimension] = value
    }

    operator fun get(dimension: Int) = values[dimension]
}

fun SaltElement.mutable() = MutableSaltElement(values = values.toMutableStateList(), type = type)
fun MutableSaltElement.immutable() = SaltElement(values, type)
