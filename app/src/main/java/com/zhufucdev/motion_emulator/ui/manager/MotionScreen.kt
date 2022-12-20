package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.dateString
import com.zhufucdev.motion_emulator.hook.estimateSpeed
import com.zhufucdev.motion_emulator.hook.estimateTimespan
import com.zhufucdev.motion_emulator.toFixed
import com.zhufucdev.motion_emulator.ui.theme.paddingCard
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall
import kotlin.random.Random
import kotlin.time.DurationUnit

@Composable
fun MotionScreen(viewModel: ManagerViewModel<Motion>) {
    DataList(viewModel) {
        Column(Modifier.padding(paddingCard)) {
            Text(text = dateString(it.time), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(paddingSmall))
            Text(
                text = stringResource(
                    R.string.text_in_duration,
                    it.estimateTimespan().toString(DurationUnit.SECONDS, decimals = 2)
                ) + (it.estimateSpeed()?.let {
                    ", " + stringResource(
                        R.string.text_estimated,
                        it.toFixed(2) + stringResource(R.string.suffix_velocity)
                    )
                } ?: ""),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(paddingSmall))
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

fun randomizedMotionData(): ManagerViewModel<Motion> {
    val data = buildList {
        repeat(10) {
            add(
                Motion(
                    NanoIdUtils.randomNanoId(),
                    System.currentTimeMillis() - Random.nextLong(
                        10000
                    ),
                    emptyList(),
                    emptyList()
                )
            )
        }
    }

    return ManagerViewModel.DummyViewModel(Screen.MotionScreen, data)
}
