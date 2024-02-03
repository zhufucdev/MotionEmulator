package com.zhufucdev.motion_emulator.extension

import android.app.Activity
import android.graphics.Color
import androidx.core.view.WindowCompat

fun Activity.setUpStatusBar() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT
}
