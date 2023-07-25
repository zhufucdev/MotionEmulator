package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.ui.plugin.PluginItem
import com.zhufucdev.motion_emulator.ui.plugin.PluginsApp
import com.zhufucdev.motion_emulator.ui.plugin.findPlugin
import com.zhufucdev.motion_emulator.ui.plugin.toPluginItem
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class PluginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MotionEmulatorTheme {
                PluginsApp(
                    onBack = this::finish,
                    plugins = Plugins.enabled.let { enabled ->
                        Plugins.available.map {
                            it.toPluginItem(enabled.contains(it))
                        }
                    },
                    onSettingsChanged = { update ->
                        Plugins.setPriorities(
                            update.mapNotNull { it.findPlugin() }
                        )
                    }
                )
            }
        }
    }
}
