package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.runtime.compositionLocalOf

/**
 * Used to provide some data for [OverviewScreen]
 */
data class ScreenProviders(val value: List<ManagerViewModel> = emptyList())

val LocalScreenProviders = compositionLocalOf { ScreenProviders() }