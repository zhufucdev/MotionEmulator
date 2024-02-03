package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.*
import com.zhufucdev.motion_emulator.extension.effectiveTimeFormat
import com.zhufucdev.motion_emulator.extension.estimateSpeed
import com.zhufucdev.motion_emulator.extension.estimateTimespan
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.motion_emulator.extension.toFixed
import com.zhufucdev.motion_emulator.ui.theme.PaddingCard
import com.zhufucdev.motion_emulator.ui.theme.PaddingSmall
import kotlin.random.Random
import kotlin.time.DurationUnit

@Composable
fun MotionScreen(viewModel: EditorViewModel<Motion>) {
    val context = LocalContext.current
    val formatter = remember { context.effectiveTimeFormat() }

    DataList(viewModel) {
        Column(Modifier.padding(PaddingCard)) {
            Text(text = it.getDisplayName(formatter), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(PaddingSmall))
            Text(
                text = stringResource(
                    R.string.text_in_duration,
                    it.estimateTimespan().toString(DurationUnit.SECONDS, decimals = 2)
                ) + (it.estimateSpeed()?.let {
                    ", " + stringResource(
                        R.string.text_estimated,
                        stringResource(R.string.suffix_velocity, it.toFloat().toFixed(2))
                    )
                } ?: ""),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(PaddingSmall))
            Text(text = it.id, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview
@Composable
fun MotionScreenPreview() {
    Surface(color = Color.White) {
        MotionScreen(randomizedMotionData())
    }
}

fun randomizedMotionData(): EditorViewModel<Motion> {
    val data = buildList {
        repeat(10) {
            add(
                Motion(
                    NanoIdUtils.randomNanoId(),
                    null,
                    System.currentTimeMillis() - Random.nextLong(
                        10000
                    ),
                    emptyList(),
                    emptyList()
                )
            )
        }
    }

    return EditorViewModel.DummyViewModel(EditableScreen.MotionScreen, data)
}
