package com.zhufucdev.motion_emulator

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes

fun getAttrColor(@AttrRes id: Int, context: Context): Int {
    val typedValue = TypedValue()
    context.theme.resolveAttribute(id, typedValue, true)
    return typedValue.data
}