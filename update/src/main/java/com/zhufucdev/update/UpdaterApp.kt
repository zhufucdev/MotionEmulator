@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@Composable
fun UpdaterApp(navigateUp: () -> Unit, updater: Updater) {
    val coroutine = rememberCoroutineScope()

    LaunchedEffect(updater) {
        if (updater.update == null) {
            updater.check()
        }
    }

    Scaffold(
        topBar = { AppBar(TopAppBarDefaults.enterAlwaysScrollBehavior(), navigateUp) },
        floatingActionButton = {
            AnimatedVisibility(visible = updater.update != null && !updater.downloading) {
                ExtendedFloatingActionButton(
                    onClick = {
                        coroutine.launch {
                            updater.download()
                        }
                    },
                    content = {
                        Icon(painterResource(R.drawable.ic_baseline_download), contentDescription = null)
                        Text(stringResource(R.string.title_download))
                    },
                )
            }
        }
    ) {
        Column(Modifier.padding(it)) {
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                Icon(painterResource(R.drawable.ic_baseline_update), contentDescription = null)
                val title =
                    if (updater.checking) {
                        stringResource(R.string.title_looking)
                    } else if (updater.update != null) {
                        stringResource(R.string.title_new_version)
                    } else {
                        stringResource(R.string.title_no_update)
                    }
                Text(title, style = MaterialTheme.typography.headlineSmall)
            }

            if (updater.downloading && updater.progress >= 0) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    progress = updater.progress
                )
            } else if (updater.checking || updater.downloading && updater.progress < 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            AnimatedVisibility(
                visible = !updater.checking && updater.update == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedIconButton(
                    onClick = {
                        coroutine.launch {
                            updater.check()
                        }
                    },
                    content = {
                        Icon(painterResource(R.drawable.ic_baseline_youtube_searched_for), contentDescription = null)
                        Text(stringResource(R.string.action_rerun))
                    }
                )
            }
        }
    }
}

@Composable
private fun AppBar(scrollBehavior: TopAppBarScrollBehavior, finish: () -> Unit) {
    LargeTopAppBar(
        title = { Text(stringResource(R.string.title_updater)) },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            PlainTooltipBox(
                tooltip = { Text(stringResource(R.string.action_navigate_up)) }
            ) {
                IconButton(
                    onClick = { finish() },
                    content = { Icons.Default.ArrowBack }
                )
            }
        }
    )
}
