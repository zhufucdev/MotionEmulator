package com.zhufucdev.motion_emulator.ui

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import com.zhufucdev.motion_emulator.extension.UPDATE_FILE_PROVIDER_AUTHORITY
import com.zhufucdev.motion_emulator.extension.Updater
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.update.Updater
import com.zhufucdev.update.ui.AbstractUpdaterActivity

class UpdaterActivity : AbstractUpdaterActivity() {
    @SuppressLint("ComposableNaming")
    @Composable
    override fun themed(content: @Composable () -> Unit) {
        MotionEmulatorTheme {
            content()
        }
    }

    override val updater: Updater
        get() = Updater(this)

    override val fileProviderAuthority: String
        get() = UPDATE_FILE_PROVIDER_AUTHORITY
}
