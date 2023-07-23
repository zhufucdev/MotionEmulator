package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.motion_emulator.ui.plugin.PluginsApp
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class PluginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionEmulatorTheme {
                PluginsApp(onBack = this::finish)
            }
        }
    }
}
