package com.zhufucdev.motion_emulator.extension

import java.math.RoundingMode
import java.text.DecimalFormat

fun Number.toFixed(n: Int): String {
    val df = DecimalFormat(buildString {
        append("#.")
        repeat(n) {
            append("#")
        }
    })
    df.roundingMode = RoundingMode.HALF_UP
    return df.format(this)
}

