package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.zhufucdev.motion_emulator.data.CellTimeline

@Composable
fun CellScreen(parameter: ManagerViewModel<CellTimeline>) {
    Text(
        text = "Greetings from cell screen",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxSize()
    )
}