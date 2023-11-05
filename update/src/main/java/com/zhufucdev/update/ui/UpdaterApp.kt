package com.zhufucdev.update.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.zhufucdev.update.R
import com.zhufucdev.update.Updater
import com.zhufucdev.update.UpdaterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdaterApp(navigateUp: () -> Unit, updater: Updater, install: suspend (File) -> Boolean) {
    val coroutine = rememberCoroutineScope { Dispatchers.Default }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(updater.update) {
        if (updater.update == null) {
            updater.check()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            updater.close()
        }
    }

    Scaffold(
        topBar = { AppBar(TopAppBarDefaults.enterAlwaysScrollBehavior(), navigateUp) },
        floatingActionButton = {
            if (updater.status is UpdaterStatus.ReadyToDownload) {
                ExtendedFloatingActionButton(
                    onClick = {
                        coroutine.launch {
                            try {
                                updater.download()
                            } catch (e: Exception) {
                                snackbar.showSnackbar(
                                    message = context.getString(
                                        R.string.text_download_failed,
                                        e.message
                                    ),
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Indefinite
                                )
                                e.printStackTrace()
                            }
                        }
                    },
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_baseline_download),
                            contentDescription = null
                        )
                    },
                    text = { Text(stringResource(R.string.title_download)) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) {
        Column(
            Modifier
                .padding(it)
                .fillMaxWidth()
        ) {
            Icon(
                painterResource(R.drawable.ic_baseline_update),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(30.dp)
            )
            Spacer(Modifier.height(12.dp))

            val title =
                when (updater.status) {
                    is UpdaterStatus.ReadyToInstall -> stringResource(R.string.title_ready_to_install)
                    is UpdaterStatus.Working.Downloading -> stringResource(R.string.title_downloading)
                    is UpdaterStatus.HasUpdate -> stringResource(R.string.text_new_version)
                    is UpdaterStatus.Working -> stringResource(R.string.text_looking)
                    else -> stringResource(R.string.text_no_update)
                }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                val status = updater.status
                if (status is UpdaterStatus.Working.Downloading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = { status.progress }
                    )
                } else if (updater.status is UpdaterStatus.Working) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            AnimatedVisibility(
                visible = updater.status == UpdaterStatus.Idling || updater.status is UpdaterStatus.ReadyToInstall,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 12.dp)
            ) {
                when (val status = updater.status) {
                    is UpdaterStatus.Idling -> {
                        Button(
                            onClick = {
                                coroutine.launch {
                                    updater.check()
                                }
                            },
                            content = {
                                Text(stringResource(R.string.action_rerun))
                            },
                        )
                    }

                    is UpdaterStatus.ReadyToInstall -> {
                        Button(
                            onClick = {
                                coroutine.launch {
                                    val success = install(status.file)
                                    if (!success) {
                                        val action = snackbar.showSnackbar(
                                            message = context.getString(R.string.text_unable_to_install_update),
                                            actionLabel = context.getString(R.string.action_reveal_in_files)
                                        )
                                        if (action == SnackbarResult.ActionPerformed) {

                                        }
                                    }
                                }
                            },
                            content = {
                                Text(stringResource(R.string.action_install))
                            }
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(scrollBehavior: TopAppBarScrollBehavior, finish: () -> Unit) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.title_updater)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            TooltipBox(
                tooltip = {
                    PlainTooltip {
                        Text(stringResource(R.string.action_navigate_up))
                    }
                },
                state = rememberTooltipState(),
                positionProvider = object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize
                    ): IntOffset {
                        return anchorBounds.bottomCenter - IntOffset(
                            popupContentSize.width / 2,
                            0
                        )
                    }
                }
            ) {
                IconButton(
                    onClick = { finish() },
                    content = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
                )
            }
        }
    )
}
