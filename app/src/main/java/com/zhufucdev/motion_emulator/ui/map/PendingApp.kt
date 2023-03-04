@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.map

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.lazySharedPreferences
import com.zhufucdev.motion_emulator.ui.component.Appendix
import com.zhufucdev.motion_emulator.ui.map.UnifiedMapFragment.Provider
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.motion_emulator.ui.theme.paddingLarge
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall

@Composable
fun PendingApp(proceeding: Class<*>, onFinish: () -> Unit) {
    var targetProvider by remember { mutableStateOf(Provider.GCP_MAPS) }
    val context = LocalContext.current
    val preference by remember { context.lazySharedPreferences() }

    Scaffold(
        topBar = { AppBar(onFinish) },
    ) {
        Column(
            modifier =
            Modifier.padding(it)
                .scrollable(rememberScrollState(), Orientation.Vertical)
        ) {
            Text(
                text = stringResource(R.string.title_is_gcs_accessible),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(paddingCommon * 2)
            )

            ExpandableOptionItem(
                title = { Text(stringResource(R.string.title_gcs_accessible)) },
                subtitle = { Text(stringResource(R.string.text_gcs_accessible)) },
                selected = targetProvider == Provider.GCP_MAPS,
                onClick = { targetProvider = Provider.GCP_MAPS },
                modifier = Modifier.fillMaxWidth().padding(paddingSmall)
            )
            ExpandableOptionItem(
                title = { Text(stringResource(R.string.title_gcs_inaccessible)) },
                subtitle = { Text(stringResource(R.string.text_gcs_inaccessible)) },
                selected = targetProvider == Provider.AMAP,
                onClick = { targetProvider = Provider.AMAP },
                modifier = Modifier.fillMaxWidth().padding(paddingSmall)
            )

            Appendix(
                { Text(stringResource(R.string.text_pending_map_provider)) },
                iconDescription = stringResource(R.string.title_activity_map_pending),
                modifier = Modifier.fillMaxWidth().padding(paddingLarge)
            )

            Button(
                content = { Text(stringResource(R.string.action_continue)) },
                onClick = {
                    preference.edit {
                        val provider = targetProvider.name.lowercase()
                        putString("map_provider", provider)
                        putString("poi_provider", provider)
                    }
                    context.startActivity(Intent(context, proceeding))
                    onFinish()
                },
                modifier = Modifier.align(Alignment.End).padding(end = paddingCommon)
            )
        }
    }
}

@Composable
private fun AppBar(navigateUp: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(
                onClick = navigateUp,
            ) {
                Icon(
                    painterResource(R.drawable.ic_baseline_arrow_back_24),
                    stringResource(R.string.action_navigate_up)
                )
            }
        }
    )
}

@Composable
private fun ExpandableOptionItem(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
                    title()
                }
                AnimatedVisibility(
                    visible = selected
                ) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                        subtitle()
                    }
                }
            }
        }
    }
}