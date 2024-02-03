package com.zhufucdev.motion_emulator.ui.composition

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf


val LocalSnackbarProvider = compositionLocalOf<SnackbarHostState?> { null }