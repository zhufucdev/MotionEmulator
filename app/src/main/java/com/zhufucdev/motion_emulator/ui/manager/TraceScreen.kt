package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.data.Trace
import com.zhufucdev.data.length
import com.zhufucdev.motion_emulator.data.MapProjector
import com.zhufucdev.motion_emulator.toFixed
import com.zhufucdev.motion_emulator.ui.theme.paddingCard
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall

@Composable
fun TraceScreen(viewModel: EditorViewModel<Trace>) {
    DataList(viewModel) {
        Column(Modifier.padding(paddingCard)) {
            Text(text = it.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(paddingSmall))
            Text(
                text = stringResource(
                    R.string.text_in_length,
                    "${it.length(MapProjector).toFixed(2)}${stringResource(R.string.suffix_meter)}"
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(paddingSmall))
            Text(text = it.id, style = MaterialTheme.typography.labelSmall)
        }
    }
}
