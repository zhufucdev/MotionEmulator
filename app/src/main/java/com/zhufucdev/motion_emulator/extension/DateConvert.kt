package com.zhufucdev.motion_emulator.extension

import java.text.DateFormat
import java.util.Date

fun DateFormat.dateString(time: Long = System.currentTimeMillis()): String =
    format(Date(time))
