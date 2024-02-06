package com.zhufucdev.motion_emulator.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.extension.toFixed
import com.zhufucdev.motion_emulator.ui.composition.ScaffoldElements
import com.zhufucdev.motion_emulator.ui.model.EmulationRef
import com.zhufucdev.motion_emulator.ui.model.EmulationsViewModel

@Composable
fun EmulateHome(paddingValues: PaddingValues) {
    val model = viewModel<EmulationsViewModel>()

    ScaffoldElements {
        floatingActionButton {
            ExtendedFloatingActionButton(
                text = { Text(text = stringResource(id = R.string.action_add)) },
                icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null) },
                onClick = { },
            )
        }
    }

    Box(modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize()) {
        if (model.configs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    modifier = Modifier.size(180.dp),
                    painter = painterResource(R.drawable.ic_thinking_face_72),
                    contentDescription = "empty",
                )
            }
        } else {
            LazyColumn {
                items(model.configs) {
                    EmulationItem(it.value)
                }
            }
        }
    }
}

@Composable
private fun EmulationItem(emulation: EmulationRef) {
    ElevatedCard {
        Column {
            Text(text = emulation.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    id = R.string.suffix_velocity,
                    emulation.velocity.toFloat().toFixed(2)
                )
            )
        }
    }
}

