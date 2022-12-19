package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.ui.manager.ManagerApp
import com.zhufucdev.motion_emulator.ui.manager.ManagerViewModel
import com.zhufucdev.motion_emulator.ui.manager.Screen
import com.zhufucdev.motion_emulator.ui.manager.ScreenParameter
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class ManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motions.require(this)
        Cells.require(this)
        Traces.require(this)

        setContent {
            MotionEmulatorTheme {
                ManagerApp(navigateUp = { finish() }, ManagerViewModel.list.map { it.parameter() })
            }
        }
    }
}
