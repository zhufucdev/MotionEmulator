@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.plugin

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhufucdev.api.ProductQuery
import com.zhufucdev.api.ReleaseAsset
import com.zhufucdev.api.findAsset
import com.zhufucdev.api.getReleaseAsset
import com.zhufucdev.motion_emulator.BuildConfig
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.extension.UPDATE_FILE_PROVIDER_AUTHORITY
import com.zhufucdev.motion_emulator.extension.Updater
import com.zhufucdev.motion_emulator.extension.defaultKtorClient
import com.zhufucdev.motion_emulator.extension.insert
import com.zhufucdev.motion_emulator.ui.CaptionText
import com.zhufucdev.motion_emulator.ui.TooltipHost
import com.zhufucdev.motion_emulator.ui.TooltipScope
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.update.Downloading
import com.zhufucdev.update.InstallationPermissionContract
import com.zhufucdev.update.StatusDownloading
import com.zhufucdev.update.StatusReadyToDownload
import com.zhufucdev.update.StatusReadyToInstall
import com.zhufucdev.update.canInstallUpdate
import com.zhufucdev.update.installUpdate
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

@Composable
fun PluginsApp(
    onBack: () -> Unit,
    plugins: List<PluginItem>,
    onSettingsChanged: (enabled: List<PluginItem>) -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )
    val snackbars = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var listBounds by remember {
        mutableStateOf<Rect?>(null)
    }
    val enabled = remember(plugins) {
        plugins.filter { it.enabled }.toMutableStateList()
    }
    val disabled = remember(plugins) {
        plugins.filter { !it.enabled }.toMutableStateList()
    }
    val downloadable = remember { mutableStateListOf<PluginItem>() }
    val updatable = remember { mutableStateMapOf<String, ReleaseAsset>() }
    val disabledList by remember(disabled) {
        derivedStateOf {
            disabled + downloadable.filter { edge -> !plugins.any { it.id == edge.id } }
        }
    }

    // something that floats is something that is being dragged
    var floating by remember { mutableStateOf<FloatingItem?>(null) }
    val hoveringItemIndex by remember(listBounds) {
        derivedStateOf {
            val localFloating = floating ?: return@derivedStateOf -1
            val localBounds = listBounds ?: return@derivedStateOf -1
            listState.layoutInfo.visibleItemsInfo.indexOfLast {
                localFloating.position.y > localBounds.top + it.offset
            }
        }
    }
    val onDrop: (PluginItem) -> Unit = remember(plugins) {
        {
            val disabledRelatedIndex = hoveringItemIndex - max(1, enabled.size) - 2
            if (disabledRelatedIndex >= 0) {
                // dropped disable
                if (!disabled.contains(it)) {
                    it.enabled = false
                    enabled.remove(it)
                    disabled.add(it)
                }
            } else {
                val enabledRelatedIndex = max(hoveringItemIndex, 0)
                if (!enabled.contains(it)) {
                    it.enabled = true
                    disabled.remove(it)
                    enabled.insert(enabledRelatedIndex, it)
                } else {
                    val original = enabled.indexOf(it)
                    enabled.remove(it)
                    if (original < enabledRelatedIndex) {
                        enabled.insert(enabledRelatedIndex - 1, it)
                    } else {
                        enabled.insert(enabledRelatedIndex, it)
                    }
                }
            }
            onSettingsChanged(enabled)
            floating = null
        }
    }

    val context = LocalContext.current
    LaunchedEffect(plugins) {
        if (downloadable.isEmpty()) {
            val queries = defaultKtorClient.findAsset(BuildConfig.SERVER_URI, "me", "plugin")
            downloadable.addAll(queries.map(ProductQuery::toPluginItem))
        }

        downloadable.forEach { edge ->
            if (!plugins.any { it.id == edge.id } || updatable.contains(edge.id)) {
                // not creating unused / duplicated network traffic
                return@forEach
            }
            val localState = edge.state
            if (localState !is PluginItemState.NotDownloaded) {
                return@forEach
            }
            val update =
                Updater(localState.query.key, context)
                    .check(context.packageManager.getPackageInfo(edge.id, 0).versionName)
            if (update != null) {
                updatable[edge.id] = update
            }
        }
        plugins.forEach {
            val update = updatable[it.id] ?: return@forEach
            it.state = PluginItemState.Update(update)
        }
    }

    TooltipHost {
        Scaffold(
            topBar = {
                PluginsAppTopBar(
                    scrollBehavior = scrollBehavior, onNavigateBack = onBack
                )
            },
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            snackbarHost = { SnackbarHost(snackbars) }
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(paddingValues)
                    .onGloballyPositioned {
                        listBounds = it.boundsInWindow()
                    }
            ) {
                // start: enable
                item {
                    CaptionText(
                        text = stringResource(id = R.string.caption_enabled),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                operativeArea(
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Favorite, contentDescription = null)
                            Text(text = stringResource(id = R.string.title_drop_to_enable))
                        }
                    },
                    plugins = enabled,
                    snackbarHostState = snackbars,
                    isHovered = hoveringItemIndex == 1,
                    onPrepareDrag = { plugin, offset, pos ->
                        floating = FloatingItem(plugin, offset, pos)
                    },
                    onDrag = {
                        floating?.position = it
                    },
                    onDrop = onDrop
                )
                // end: enable
                // start: disable
                item {
                    CaptionText(
                        text = stringResource(id = R.string.caption_disabled),
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                operativeArea(
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Build, contentDescription = null)
                            Text(text = stringResource(id = R.string.title_drop_to_disable))
                        }
                    },
                    plugins = disabledList,
                    snackbarHostState = snackbars,
                    isHovered = hoveringItemIndex == enabled.size + 3,
                    onPrepareDrag = { plugin, offset, pos ->
                        floating = FloatingItem(plugin, offset, pos)
                    },
                    onDrag = {
                        floating?.position = it
                    },
                    onDrop = onDrop
                )
            }
        }
    }

    floating?.let {
        FloatingItemView(data = it)
    }
}

@Composable
private fun FloatingItemView(data: FloatingItem) {
    val offset = data.position + data.offset
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
        with(LocalDensity.current) {
            PluginItemView(
                modifier = Modifier
                    .absoluteOffset(offset.x.toDp(), offset.y.toDp()),
                item = data.plugin
            )
        }
    }
}

@Stable
private class FloatingItem(val plugin: PluginItem, val offset: Offset, position: Offset) {
    var position: Offset by mutableStateOf(position)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FloatingItem

        if (plugin != other.plugin) return false
        return offset == other.offset
    }

    override fun hashCode(): Int {
        var result = plugin.hashCode()
        result = 31 * result + offset.hashCode()
        return result
    }
}

private fun Modifier.dragTarget(
    prepare: (Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    stop: () -> Unit
): Modifier =
    composed {
        var offset by remember {
            mutableStateOf(Offset(0f, 0f))
        }
        var position by remember {
            mutableStateOf(Offset(0f, 0f))
        }
        var isDragging by remember {
            mutableStateOf(false)
        }
        then(
            Modifier
                .onGloballyPositioned {
                    if (!isDragging) {
                        position =
                            it.positionInWindow() - Offset(0f, it.boundsInWindow().height / 2)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            offset = it + position
                            prepare(-it, offset)
                        },
                        onDragEnd = {
                            stop()
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            offset += dragAmount
                            onDrag(offset)
                        }
                    )
                }
        )
    }

@Composable
private fun TooltipScope.PluginsAppTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit
) {
    LargeTopAppBar(title = { Text(text = stringResource(id = R.string.title_activity_plugin)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.tooltip { Text(text = stringResource(R.string.action_navigate_up)) }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.action_navigate_up)
                )
            }
        })
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.operativeArea(
    label: @Composable () -> Unit,
    plugins: List<PluginItem>,
    snackbarHostState: SnackbarHostState,
    isHovered: Boolean,
    onPrepareDrag: (PluginItem, Offset, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDrop: (plugin: PluginItem) -> Unit,
) {
    plugins.forEach {
        val mod =
            if (it.state is PluginItemState.NotDownloaded) {
                Modifier
            } else {
                Modifier
                    .dragTarget(
                        prepare = { off, pos ->
                            onPrepareDrag(it, off, pos)
                        },
                        onDrag = onDrag,
                        stop = {
                            onDrop(it)
                        }
                    )
            }

        item(it.id) {
            val coroutine = rememberCoroutineScope()
            val context = LocalContext.current

            val updater = remember { Updater(context) }
            val launcher = rememberLauncherForActivityResult(
                remember {
                    // this should be remembered, or
                    // class be made into single instance
                    InstallationPermissionContract()
                },
            ) { canInstall ->
                if (canInstall) {
                    val status = updater.status
                    if (status is StatusReadyToInstall) {
                        context.installUpdate(status.file, UPDATE_FILE_PROVIDER_AUTHORITY)
                    }
                }
            }
            val downloadProgress by remember(updater.status) {
                derivedStateOf {
                    when (val status = updater.status) {
                        is StatusDownloading -> {
                            status.progress
                        }

                        is StatusReadyToDownload -> {
                            0f
                        }

                        else -> {
                            -1f
                        }
                    }
                }
            }

            fun notifyInstall(update: File) {
                if (!context.packageManager.canInstallUpdate()) {
                    launcher.launch(context.packageName)
                } else {
                    context.installUpdate(update, UPDATE_FILE_PROVIDER_AUTHORITY)
                }
            }

            Surface(
                onClick = {
                    val status = updater.status
                    if (status is Downloading) {
                        return@Surface
                    } else if (status is StatusReadyToInstall) {
                        notifyInstall(status.file)
                        return@Surface
                    }

                    val state = it.state
                    if (state.containsDownloadable) {
                        coroutine.launch {
                            val asset = when (state) {
                                is PluginItemState.NotDownloaded -> defaultKtorClient.getReleaseAsset(
                                    BuildConfig.SERVER_URI,
                                    state.query.key
                                )

                                is PluginItemState.Update -> {
                                    state.asset
                                }

                                else -> {
                                    throw NotImplementedError(state.toString())
                                }
                            }
                            if (asset == null) {
                                snackbarHostState.showSnackbar(context.getString(R.string.text_asset_fetch_failed))
                                return@launch
                            }
                            val update = try {
                                updater.download(asset)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(context.getString(R.string.text_asset_fetch_failed))
                                return@launch
                            }
                            notifyInstall(update)
                        }
                    }
                },
                modifier = mod.animateItemPlacement()
            ) {
                PluginItemView(
                    item = it,
                    progress = downloadProgress,
                    modifier = Modifier.fillMaxWidth(),
                    innerModifier = Modifier.padding(horizontal = paddingCommon * 2)
                )
            }
        }
    }
    item {
        var ratio by remember { mutableFloatStateOf(1.618f) }

        LaunchedEffect(isHovered) {
            if (plugins.isNotEmpty()) {
                ratio = 1.618f
                return@LaunchedEffect
            }
            if (isHovered) {
                animate(ratio, 1f) { v, _ ->
                    ratio = v
                }
            } else {
                animate(ratio, 1.618f) { v, _ ->
                    ratio = v
                }
            }
        }

        DropArea(
            label = label,
            color = (
                    if (isHovered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                    ),
            visible = plugins.isEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = paddingCommon)
                .aspectRatio(ratio)
        )
    }
}

@Composable
private fun PluginItemView(
    modifier: Modifier = Modifier,
    innerModifier: Modifier = Modifier,
    item: PluginItem,
    progress: Float = -1f,
) {
    Box(modifier) {
        AnimatedVisibility(
            visible = progress >= 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .matchParentSize()
                .alpha(0.5f)
        ) {
            if (progress > 0) {
                LinearProgressIndicator(progress)
            } else {
                LinearProgressIndicator()
            }
        }

        Row(
            modifier = Modifier
                .padding(paddingCommon)
                .then(innerModifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // start: front icon
            when (item.state) {
                is PluginItemState.NotDownloaded -> {
                    Icon(
                        painter = painterResource(id = com.zhufucdev.update.R.drawable.ic_baseline_download),
                        contentDescription = stringResource(id = R.string.des_plugin_downloadable),
                    )
                }

                is PluginItemState.Update -> {
                    Icon(
                        painter = painterResource(id = com.zhufucdev.update.R.drawable.ic_baseline_update),
                        contentDescription = stringResource(id = R.string.des_plugin_update),
                    )
                }

                is PluginItemState.None -> {
                    if (item.enabled) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_power_plug),
                            contentDescription = stringResource(id = R.string.des_plugin_enabled),
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_power_plug_off),
                            contentDescription = stringResource(id = R.string.des_plugin_disabled),
                        )
                    }
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
                        is PluginItemState.NotDownloaded -> Text(stringResource(id = R.string.des_plugin_downloadable))
                        is PluginItemState.Update -> Text(stringResource(id = R.string.des_plugin_update))
                        else -> {
                            if (item.subtitle.isNotBlank()) {
                                Text(text = item.subtitle)
                            }
                        }
                    }
                }
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
                    enabled = true,
                    state = PluginItemState.None
                )
            )
            PluginItemView(
                item = PluginItem(
                    "e2",
                    "example plug-in 2",
                    "hi",
                    enabled = true,
                    state = PluginItemState.None
                )
            )
            PluginItemView(
                item = PluginItem(
                    "e3",
                    "example plug-in 3",
                    enabled = true,
                    state = PluginItemState.Update(ReleaseAsset("", "", ""))
                ),
                progress = 0f,
            )
        }
    }
}

@Composable
private fun DropArea(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    label: @Composable () -> Unit,
    visible: Boolean
) {
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(
            textAlign = TextAlign.Center,
            color = color
        ),
        LocalContentColor provides color
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(modifier) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(16F))
                ) {
                    drawRoundRect(
                        color = color,
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
}

@Preview(showBackground = true)
@Composable
fun PluginsAppPreview() {
    MotionEmulatorTheme {
        PluginsApp(onBack = {}, plugins = emptyList())
    }
}