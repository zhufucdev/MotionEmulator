package com.zhufucdev.motion_emulator.extension

import android.content.res.Configuration
import android.content.res.Resources

fun isDarkModeEnabled(resources: Resources) =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

