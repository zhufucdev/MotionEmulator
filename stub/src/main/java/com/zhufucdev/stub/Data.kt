package com.zhufucdev.stub

import java.io.OutputStream
import java.text.DateFormat

/**
 * Represents something that can be referred
 * with barely a string ID
 */
interface Data {
    val id: String
    fun getDisplayName(format: DateFormat): String
    fun writeTo(stream: OutputStream)
}