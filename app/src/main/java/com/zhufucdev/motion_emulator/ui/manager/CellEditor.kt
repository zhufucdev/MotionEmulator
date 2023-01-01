package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zhufucdev.motion_emulator.data.CellTimeline

@Composable
fun CellEditor(target: CellTimeline, viewModel: ManagerViewModel<CellTimeline>) {
    Text(text = "Greetings from cell editor")
}