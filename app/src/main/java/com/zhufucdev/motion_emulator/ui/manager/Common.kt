package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.component.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.theme.PaddingCommon
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

fun LazyListScope.basicEditItems(
    id: String,
    name: String,
    onNameChanged: (String) -> Unit,
    bottomMargin: Dp = PaddingCommon * 2,
    icon: @Composable () -> Unit
) {
    item {
        TextField(
            label = { Text(stringResource(id = R.string.title_id)) },
            value = id,
            onValueChange = {},
            leadingIcon = icon,
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        VerticalSpacer()
    }

    item {
        TextField(
            label = { Text(stringResource(id = R.string.title_name)) },
            value = name,
            onValueChange = onNameChanged,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_edit_24),
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        VerticalSpacer(bottomMargin)
    }
}

@Composable
fun BasicEdit(
    id: String,
    name: String,
    onNameChanged: (String) -> Unit,
    icon: @Composable () -> Unit
) {
    var rename by remember { mutableStateOf(name) }
    var committed by remember { mutableStateOf(false) }
    LaunchedEffect(rename) {
        val captured = rename
        committed = false
        delay(1.seconds)

        if (rename != captured) return@LaunchedEffect
        onNameChanged(captured)
        committed = true
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!committed && rename != name) {
                onNameChanged(rename)
            }
        }
    }

    LazyColumn {
        basicEditItems(
            id = id,
            name = rename,
            onNameChanged = {
                rename = it
            },
            icon = icon
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    divider: Boolean = true
) {
    Column {
        Surface(onClick = onClick) {
            Row(
                Modifier.padding(horizontal = PaddingCommon * 2, vertical = PaddingCommon)
                    .then(modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingIcon()
                Spacer(Modifier.width(PaddingCommon * 2))
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                    title()
                }
            }
        }
        if (divider) {
            HorizontalDivider(Modifier.padding(horizontal = PaddingCommon))
        }
    }
}