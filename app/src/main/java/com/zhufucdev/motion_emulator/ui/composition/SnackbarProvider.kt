package com.zhufucdev.motion_emulator.ui.composition

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf

data class SnackbarProvider(val controller: SnackbarHostState?)

val LocalSnackbarProvider = compositionLocalOf { SnackbarProvider(null) }