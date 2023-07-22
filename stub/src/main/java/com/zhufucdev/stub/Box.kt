package com.zhufucdev.stub

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

open class Box<T>(val value: T? = null) {
    open val status: Toggle
        get() = Toggle.PRESENT

    override fun equals(other: Any?): Boolean =
        other is Box<*> && other.value == value && other.status == status

    override fun hashCode(): Int {
        return (value?.hashCode() ?: 0) + status.hashCode()
    }

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

enum class Toggle {
    BLOCK, NONE, PRESENT
}

fun <T> T.box(): Box<T> = Box(this)
fun <T> T.boxOrEmpty(): Box<T> = this?.let { Box(it) } ?: EmptyBox()

const val EMPTY_REF = "none"
const val BLOCK_REF = "block"
const val NULL_REF = "null"

inline fun <reified T> Box<T>.encodeToString(): String =
    when (this) {
        is EmptyBox -> EMPTY_REF
        is BlockBox -> BLOCK_REF
        else -> value?.let { Json.encodeToString(serializer<T>(), it) } ?: "null"
    }


class MutableBox<T>(var value: T)

fun <T> T.mutbox() = MutableBox(this)
