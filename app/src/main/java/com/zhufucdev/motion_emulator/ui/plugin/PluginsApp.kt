@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.plugin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.CaptionText
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

@Composable
fun PluginsApp(onBack: () -> Unit) {
    Scaffold(topBar = {
        PluginsAppTopBar(
            scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
                rememberTopAppBarState()
            ),
            onNavigateBack = onBack
        )
    }) {
        Column(Modifier.padding(it)) {
            CaptionText(
                text = stringResource(id = R.string.caption_enabled),
                modifier = Modifier.padding(start = 16.dp)
            )
            EnabledSection()
        }
    }
}

@Composable
private fun PluginsAppTopBar(scrollBehavior: TopAppBarScrollBehavior, onNavigateBack: () -> Unit) {
    LargeTopAppBar(
        title = { Text(text = stringResource(id = R.string.title_activity_plugin)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_up)
                )
            }
        }
    )
}

@Composable
private fun EnabledSection() {
    DropArea(
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Favorite, contentDescription = null)
                Text(text = stringResource(id = R.string.title_drop_to_enable))
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 36.dp, top = 12.dp)
            .aspectRatio(1F)
    )
}

@Composable
private fun DropArea(label: @Composable () -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
    ) {
        Box(modifier) {
            val colors = MaterialTheme.colorScheme
            Canvas(
                modifier = Modifier
                    .defaultMinSize(120.dp, 120.dp)
                    .matchParentSize()
                    .clip(RoundedCornerShape(16F))
            ) {
                drawRoundRect(
                    color = colors.onSurface,
                    cornerRadius = CornerRadius(16F, 16F),
                    style = Stroke(
                        width = 20F,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 20f)),
                    )
                )
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(4.dp)
            ) {
                label()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PluginsAppPreview() {
    MotionEmulatorTheme {
        PluginsApp(onBack = {})
    }
}