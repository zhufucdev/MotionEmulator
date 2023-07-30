package com.zhufucdev.mock_location_plugin.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import com.zhufucdev.mock_location_plugin.FILE_PROVIDER_AUTHORITIES
import com.zhufucdev.mock_location_plugin.updater
import com.zhufucdev.motion_emulator.ui.theme.PluginTheme
import com.zhufucdev.update.*
import com.zhufucdev.update.ui.AbstractUpdaterActivity

class UpdaterActivity : AbstractUpdaterActivity() {

    @SuppressLint("ComposableNaming")
    @Composable
    override fun themed(content: @Composable () -> Unit) {
        PluginTheme {
            content()
        }
    }

    override val updater: Updater
        get() = updater()

    override val fileProviderAuthority: String
        get() = FILE_PROVIDER_AUTHORITIES
}