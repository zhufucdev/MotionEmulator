package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.dateString
import com.zhufucdev.motion_emulator.hook.estimateTimespan
import com.zhufucdev.motion_emulator.ui.theme.paddingCard
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotionScreen(parameter: ScreenParameter<Motion>) {
    DataList(data = parameter.data) {
        Card(
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingCommon, paddingCommon / 2),
            onClick = { parameter.handler(it) }
        ) {
            Column(Modifier.padding(paddingCard)) {
                Text(text = dateString(it.time), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(paddingSmall))
                Text(
                    text = stringResource(R.string.text_in_duration, it.estimateTimespan().toString()),
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(paddingSmall))
                Text(text = it.id, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Preview
@Composable
fun MotionScreenPreview() {
    Surface(color = Color.White) {
        MotionScreen(randomMotionData())
    }
}
