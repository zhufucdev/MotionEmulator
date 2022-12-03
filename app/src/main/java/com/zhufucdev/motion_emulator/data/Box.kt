package com.zhufucdev.motion_emulator.data

import com.zhufucdev.motion_emulator.hook.Toggle
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

open class Box<T>(val value: T? = null) {
    open val status: Toggle
        get() = Toggle.PRESENT

    companion object {
        inline fun <reified T> decodeFromString(str: String): Box<T> {
            val value = when (str) {
                EMPTY_REF -> return EmptyBox()
                BLOCK_REF -> return BlockBox()
                NULL_REF -> null
                else -> Json.decodeFromString(serializer<T>(), str)
            }
            return Box(value)
        }
    }
}

class EmptyBox<T> : Box<T>() {
    override val status: Toggle
        get() = Toggle.NONE
}

class BlockBox<T> : Box<T>() {
    override val status: Toggle
        get() = Toggle.BLOCK
}

fun <T> T.box(): Box<T> = Box(this)

const val EMPTY_REF = "none"
const val BLOCK_REF = "block"
const val NULL_REF = "null"

inline fun <reified T> Box<T>.encodeToString(): String =
    when (this) {
        is EmptyBox -> EMPTY_REF
        is BlockBox -> BLOCK_REF
        else -> value?.let { Json.encodeToString(serializer<T>(), it) } ?: "null"
    }
