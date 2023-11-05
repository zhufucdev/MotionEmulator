package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.zhufucdev.motion_emulator.extension.setUpStatusBar
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.ui.plugin.PluginsApp
import com.zhufucdev.motion_emulator.ui.plugin.findPlugin
import com.zhufucdev.motion_emulator.ui.plugin.toPluginItem
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class PluginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpStatusBar()

        setContent {
            val plugins = remember(Plugins.available) {
                Plugins.enabled.let { enabled ->
                    Plugins.available.map {
                        it.toPluginItem(
                            enabled = enabled.contains(it)
                        )
                    }
                }
            }

            MotionEmulatorTheme {
                PluginsApp(
                    onBack = this::finish,
                    plugins = plugins,
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
