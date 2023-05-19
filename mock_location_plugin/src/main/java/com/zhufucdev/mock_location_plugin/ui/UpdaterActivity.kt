package com.zhufucdev.mock_location_plugin.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.mock_location_plugin.BuildConfig
import com.zhufucdev.mock_location_plugin.FILE_PROVIDER_AUTHORITIES
import com.zhufucdev.mock_location_plugin.updater
import com.zhufucdev.motion_emulator.ui.theme.PluginTheme
import com.zhufucdev.update.*

class UpdaterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = SuspendedActivityResultLauncher(this)

        setContent {
            PluginTheme {
                UpdaterApp(
                    navigateUp = { finish() },
                    updater = updater(),
                    install = {
                        if (requireInstallerPermission(launcher)) {
                            installUpdate(it, FILE_PROVIDER_AUTHORITIES)
                        } else {
                            false
                        }
                    }
                )
            }
        }
    }
}