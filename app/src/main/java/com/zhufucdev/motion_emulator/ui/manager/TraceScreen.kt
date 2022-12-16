package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.zhufucdev.motion_emulator.data.Trace

@Composable
fun TraceScreen(parameter: ScreenParameter<Trace>) {
    Text(
        text = "Greetings from trace screen",
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxSize()
    )
}
