package com.zhufucdev.motion_emulator.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.zhufucdev.motion_emulator.ui.theme.PaddingCommon

@Composable
fun VerticalSpacer(space: Dp = PaddingCommon) {
    Spacer(Modifier.height(space))
}

@Composable
fun HorizontalSpacer(space: Dp = PaddingCommon) {
    Spacer(Modifier.width(space))
}

