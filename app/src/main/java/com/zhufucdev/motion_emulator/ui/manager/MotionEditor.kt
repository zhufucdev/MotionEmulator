package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zhufucdev.motion_emulator.data.Motion

@Composable
fun MotionEditor(target: Motion, viewModel: ManagerViewModel<Motion>) {
    Text(text = "Greetings from motion editor")
}