package com.zhufucdev.motion_emulator.data

import java.io.OutputStream

/**
 * Represents something that can be referred
 * with barely a string ID
 */
interface Data {
    val id: String
    val displayName: String
    fun writeTo(stream: OutputStream)
}