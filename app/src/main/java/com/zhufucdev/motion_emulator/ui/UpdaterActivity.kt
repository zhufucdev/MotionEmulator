package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.updater
import com.zhufucdev.update.UpdaterApp

class UpdaterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionEmulatorTheme {
                UpdaterApp(
                    navigateUp = { finish() },
                    updater = updater()
                )
            }
        }
    }
}
