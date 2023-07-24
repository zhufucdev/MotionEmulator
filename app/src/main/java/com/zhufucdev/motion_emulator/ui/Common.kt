package com.zhufucdev.motion_emulator.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun VerticalSpacer(space: Dp = paddingCommon) {
    Spacer(Modifier.height(space))
}

@Composable
fun HorizontalSpacer(space: Dp = paddingCommon) {
    Spacer(Modifier.width(space))
}

@Composable
fun CaptionText(modifier: Modifier = Modifier, text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
    )
}