package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.data.CellTimeline
import com.zhufucdev.motion_emulator.effectiveTimeFormat
import com.zhufucdev.motion_emulator.ui.theme.paddingCard
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall

@Composable
fun CellScreen(viewModel: EditorViewModel<CellTimeline>) {
    val context = LocalContext.current
    val formatter = remember { context.effectiveTimeFormat() }

    DataList(viewModel) {
        Column(Modifier.padding(paddingCard)) {
            Text(text = it.getDisplayName(formatter), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(paddingSmall))
            Text(
                text = stringResource(R.string.text_pieces_of_record, it.moments.size),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(paddingSmall))
            Text(text = it.id, style = MaterialTheme.typography.labelSmall)
        }
    }
}