package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.setUpStatusBar
import com.zhufucdev.motion_emulator.ui.manager.*
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class ManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motions.require(this)
        Cells.require(this)
        Traces.require(this)
        setUpStatusBar()

        setContent {
            MotionEmulatorTheme {
                CompositionLocalProvider(LocalScreenProviders provides ScreenProviders(viewModels)) {
                    ManagerApp(navigateUp = { finish() })
                }
            }
        }
    }

    private val viewModels: List<ManagerViewModel> by lazy {
        val provider = ViewModelProvider(this)
        listOf(
            provider[ManagerViewModel.OverviewViewModel::class.java],
            provider[EditorViewModel.MotionViewModel::class.java],
            provider[EditorViewModel.CellsViewModel::class.java],
            provider[EditorViewModel.TraceViewModel::class.java]
        )
    }
}
