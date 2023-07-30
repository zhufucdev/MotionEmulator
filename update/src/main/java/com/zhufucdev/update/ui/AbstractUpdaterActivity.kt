package com.zhufucdev.update.ui

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.zhufucdev.update.SuspendedActivityResultLauncher
import com.zhufucdev.update.Updater
import com.zhufucdev.update.installUpdate
import com.zhufucdev.update.requireInstallerPermission

abstract class AbstractUpdaterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcher = SuspendedActivityResultLauncher(this)

        setContent {
            themed {
                UpdaterApp(
                    navigateUp = { finish() },
                    updater = updater,
                    install = {
                        if (requireInstallerPermission(launcher)) {
                            installUpdate(it, fileProviderAuthority)
                        } else {
                            false
                        }
                    },
                )
            }
        }
    }

    abstract val updater: Updater
    abstract val fileProviderAuthority: String

    @SuppressLint("ComposableNaming")
    @Composable
    abstract fun themed(content: @Composable () -> Unit)
}
