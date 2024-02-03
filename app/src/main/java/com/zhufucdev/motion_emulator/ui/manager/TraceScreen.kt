package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.me.stub.length
import com.zhufucdev.motion_emulator.extension.toFixed
import com.zhufucdev.motion_emulator.ui.theme.PaddingCard
import com.zhufucdev.motion_emulator.ui.theme.PaddingSmall

@Composable
fun TraceScreen(viewModel: EditorViewModel<Trace>) {
    DataList(viewModel) {
        Column(Modifier.padding(PaddingCard)) {
            Text(text = it.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(PaddingSmall))
            Text(
                text = stringResource(
                    R.string.text_in_length,
                    "${it.length().toFloat().toFixed(2)}${stringResource(R.string.suffix_meter)}"
                ),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(PaddingSmall))
            Text(text = it.id, style = MaterialTheme.typography.labelSmall)
        }
    }
}
