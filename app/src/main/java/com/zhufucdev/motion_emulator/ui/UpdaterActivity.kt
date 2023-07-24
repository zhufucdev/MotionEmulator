package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.motion_emulator.extension.FILE_PROVIDER_AUTHORITY
import com.zhufucdev.motion_emulator.extension.Updater
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.update.SuspendedActivityResultLauncher
import com.zhufucdev.update.UpdaterApp
import com.zhufucdev.update.installUpdate
import com.zhufucdev.update.requireInstallerPermission

class UpdaterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = SuspendedActivityResultLauncher(this)

        setContent {
            MotionEmulatorTheme {
                UpdaterApp(
                    navigateUp = { finish() },
                    updater = Updater(this),
                    install = {
                        if (requireInstallerPermission(launcher))
                            installUpdate(it, FILE_PROVIDER_AUTHORITY)
                        else
                            false
                    },
                )
            }
        }
    }
}
