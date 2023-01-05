package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
fun LazyListScope.basicEditItems(
    id: String,
    name: String,
    onNameChanged: (String) -> Unit,
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
        VerticalSpacer(paddingCommon * 2)
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