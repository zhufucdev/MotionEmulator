package com.zhufucdev.motion_emulator.extension

import androidx.compose.ui.geometry.Offset
import com.zhufucdev.me.stub.Vector2D


fun Vector2D.toOffset() = Offset(x.toFloat(), y.toFloat())
fun Offset.toVector2d() = Vector2D(x * 1.0, y * 1.0)

