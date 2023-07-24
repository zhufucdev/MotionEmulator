@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.plugin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import com.zhufucdev.api.ProductQuery
import com.zhufucdev.api.findAsset
import com.zhufucdev.motion_emulator.BuildConfig
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.extension.defaultKtorClient
import com.zhufucdev.motion_emulator.ui.CaptionText
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.ui.theme.iconMargin
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun PluginsApp(onBack: () -> Unit, plugins: List<PluginItem>) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val enabled = remember {
        plugins.filter { it.state == PluginItemState.ENABLED }.toMutableStateList()
    }
    val disabled = remember {
        plugins.filter { it.state == PluginItemState.DISABLED }.toMutableStateList()
    }
    val downloadable = remember { mutableStateListOf<PluginItem>() }
    var list by remember { mutableStateOf(listOf<PluginItem>()) }

    LaunchedEffect(Unit) {
        val queries = defaultKtorClient.findAsset(BuildConfig.SERVER_URI, "me", "plugin")
        downloadable.addAll(queries.map(ProductQuery::toPluginItem))
    }

    LaunchedEffect(downloadable, disabled) {
        // do not list downloadable when it's already downloaded but disabled
        list = disabled + downloadable.filter { edge -> !disabled.any { it.id == edge.id } }
    }

    Scaffold(
        topBar = {
            PluginsAppTopBar(
                scrollBehavior = scrollBehavior, onNavigateBack = onBack
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { paddingValues ->
        LazyColumn(
            Modifier
                .padding(paddingValues)
        ) {
            item {
                CaptionText(
                    text = stringResource(id = R.string.caption_enabled),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            operativeSection(
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Text(text = stringResource(id = R.string.title_drop_to_enable))
                    }
                },
                pluginList = {

                },
                isEmpty = enabled.isEmpty()
            )

            item {
                CaptionText(
                    text = stringResource(id = R.string.caption_disabled),
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            operativeSection(
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Text(text = stringResource(id = R.string.title_drop_to_disable))
                    }
                },
                pluginList = {
                    list.forEach {
                        item(key = it.id) {
                            Surface {
                                PluginItemView(
                                    item = it,
                                    modifier = Modifier
                                        .padding(horizontal = paddingCommon * 2)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                isEmpty = list.isEmpty()
            )
        }
    }
}

@Composable
private fun PluginsAppTopBar(scrollBehavior: TopAppBarScrollBehavior, onNavigateBack: () -> Unit) {
    LargeTopAppBar(title = { Text(text = stringResource(id = R.string.title_activity_plugin)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_up)
                )
            }
        })
}

private fun LazyListScope.operativeSection(
    label: @Composable () -> Unit,
    pluginList: LazyListScope.() -> Unit,
    isEmpty: Boolean
) {
    pluginList()
    item {
        if (isEmpty) {
            DropArea(
                label = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = paddingCommon)
                    .aspectRatio(1F)
            )
        }
    }
}

@Composable
private fun PluginItemView(
    modifier: Modifier = Modifier,
    item: PluginItem,
    progress: Float = -1f,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.padding(paddingCommon),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // start: front icon
            when (item.state) {
                PluginItemState.NOT_DOWNLOADED -> {
                    Icon(
                        painter = painterResource(id = com.zhufucdev.update.R.drawable.ic_baseline_download),
                        contentDescription = stringResource(id = R.string.des_plugin_downloadable),
                    )
                }

                PluginItemState.UPDATE -> {
                    Icon(
                        painter = painterResource(id = com.zhufucdev.update.R.drawable.ic_baseline_update),
                        contentDescription = stringResource(id = R.string.des_plugin_update),
                    )
                }

                PluginItemState.ENABLED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_power_plug),
                        contentDescription = stringResource(id = R.string.des_plugin_enabled),
                    )
                }

                PluginItemState.DISABLED -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_power_plug_off),
                        contentDescription = stringResource(id = R.string.des_plugin_disabled),
                    )
                }
            }
            // end: front icon
            // titles
            Column(
                Modifier
                    .padding(start = paddingCommon)
                    .fillMaxWidth()
            ) {
                Text(text = item.title, style = MaterialTheme.typography.titleSmall)
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelSmall
                ) {
                    when (item.state) {
                        PluginItemState.NOT_DOWNLOADED -> Text(stringResource(id = R.string.des_plugin_downloadable))
                        PluginItemState.UPDATE -> Text(stringResource(id = R.string.des_plugin_update))
                        else -> {
                            if (item.subtitle.isNotBlank()) {
                                Text(text = item.subtitle)
                            }
                        }
                    }
                }
            }
        }

        if (progress >= 0) {
            if (progress > 0) {
                LinearProgressIndicator(progress, Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PluginItemPreview() {
    MotionEmulatorTheme {
        Column {
            PluginItemView(
                item = PluginItem(
                    "e1",
                    "example plug-in 1",
                    state = PluginItemState.ENABLED
                )
            )
            PluginItemView(
                item = PluginItem(
                    "e2",
                    "example plug-in 2",
                    "hi",
                    PluginItemState.ENABLED
                )
            )
            PluginItemView(
                item = PluginItem("e3", "example plug-in 3", state = PluginItemState.UPDATE),
                progress = 0f,
            )
        }
    }
}

@Composable
private fun DropArea(modifier: Modifier = Modifier, label: @Composable () -> Unit) {
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
                    color = colors.onSurface, cornerRadius = CornerRadius(16F, 16F), style = Stroke(
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
        PluginsApp(onBack = {}, plugins = emptyList())
    }
}