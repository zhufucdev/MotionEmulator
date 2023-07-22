package com.zhufucdev.motion_emulator.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.*
import com.zhufucdev.motion_emulator.ui.theme.*
import com.zhufucdev.update.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHome(updater: Updater, onClick: (AppHomeDestination) -> Unit) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(updater.update) {
        if (updater.update != null) {
            val callback = snackbar.showSnackbar(
                context.getString(R.string.text_update_found),
                context.getString(R.string.action_upgrade),
                true
            )
            if (callback == SnackbarResult.ActionPerformed) {
                context.startActivity(Intent(context, UpdaterActivity::class.java))
            }
        }
    }

    Scaffold(
        topBar = {
            HomeAppbar(
                onClick = onClick,
                scrollBehavior = behavior
            )
        },
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) }
    ) {
        LazyColumn(modifier = Modifier.padding(it)) {
            item {
                HomeCard(
                    onClick = { onClick(AppHomeDestination.Plugins) },
                    title = stringResource(id = R.string.title_status_activated),
                    subtitle = stringResource(id = R.string.text_status_activated),
                    icon = painterResource(id = R.drawable.ic_baseline_done_all_24),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }

            item {
                HomeCard(
                    onClick = { onClick(AppHomeDestination.Record) },
                    title = stringResource(id = R.string.title_record),
                    subtitle = stringResource(id = R.string.text_record),
                    icon = painterResource(id = R.drawable.ic_baseline_smartphone_24)
                )
            }

            item {
                HomeCard(
                    onClick = { onClick(AppHomeDestination.Trace) },
                    title = stringResource(id = R.string.title_draw_trace),
                    subtitle = stringResource(id = R.string.text_draw_trace),
                    icon = painterResource(id = R.drawable.ic_baseline_map_24)
                )
            }

            item {
                HomeCard(
                    onClick = { onClick(AppHomeDestination.Management) },
                    title = stringResource(id = R.string.title_manage),
                    subtitle = stringResource(id = R.string.text_manage),
                    icon = painterResource(id = R.drawable.ic_baseline_app_registration_24)
                )
            }

            item {
                HomeCard(
                    onClick = { onClick(AppHomeDestination.Emulation) },
                    title = stringResource(id = R.string.title_emulate),
                    subtitle = stringResource(id = R.string.text_emulate),
                    icon = painterResource(id = R.drawable.ic_baseline_auto_fix_high_24)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCard(
    onClick: () -> Unit,
    title: String,
    subtitle: String,
    icon: Painter,
    colors: CardColors = CardDefaults.cardColors()
) {
    Card(
        onClick = onClick,
        colors = colors,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = paddingLarge,
                end = paddingLarge,
                bottom = paddingLarge
            )
    ) {
        Column(
            modifier = Modifier.padding(paddingCard)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = title, modifier = Modifier.padding(iconMargin))
                Text(text = title, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(paddingSmall))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = iconMargin)
            )
            Spacer(modifier = Modifier.height(paddingSmall))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppbar(onClick: (AppHomeDestination) -> Unit, scrollBehavior: TopAppBarScrollBehavior) {
    LargeTopAppBar(
        title = { Text(text = stringResource(R.string.app_name)) },
        actions = {
            PlainTooltipBox(
                tooltip = { Text(stringResource(R.string.title_activity_settings)) },
                content = {
                    IconButton(
                        onClick = { onClick(AppHomeDestination.Settings) },
                        modifier = Modifier.tooltipTrigger(),
                        content = {
                            Icon(
                                Icons.Default.Settings,
                                stringResource(R.string.title_activity_settings),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            )
        },
        scrollBehavior = scrollBehavior
    )
}

enum class AppHomeDestination(val activity: Class<*>, val mapping: Boolean = false) {
    Plugins(PluginActivity::class.java),
    Record(RecordActivity::class.java),
    Trace(TraceDrawingActivity::class.java, true),
    Emulation(EmulateActivity::class.java, true),
    Management(ManagerActivity::class.java),
    Settings(SettingsActivity::class.java)
}
