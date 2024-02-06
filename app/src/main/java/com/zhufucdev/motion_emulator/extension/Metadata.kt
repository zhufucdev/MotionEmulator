package com.zhufucdev.motion_emulator.extension

import android.content.Context
import com.zhufucdev.me.stub.Metadata
import java.text.DateFormat
import java.util.Date

fun Metadata.displayName(context: Context): String {
    return name ?: DateFormat.getDateTimeInstance().format(Date(creationTime))
}