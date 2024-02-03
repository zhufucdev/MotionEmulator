package com.zhufucdev.motion_emulator.ui.composition

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

val LocalNestedScrollConnectionProvider = compositionLocalOf<NestedScrollConnection?> { null }