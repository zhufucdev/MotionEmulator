package com.zhufucdev.motion_emulator.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun Appendix(
    vararg paragraphs: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    iconDescription: String? = null
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Icon(
            painter = painterResource(R.drawable.outline_info_24),
            contentDescription = iconDescription,
            tint = color
        )
        VerticalSpacer(paddingCommon)
        CompositionLocalProvider(
            LocalContentColor provides color,
            LocalTextStyle provides MaterialTheme.typography.bodyMedium
        ) {
            paragraphs.forEachIndexed { index, paragraph ->
                paragraph()
                if (index < paragraphs.lastIndex) {
                    VerticalSpacer()
                }
            }
        }
    }
}