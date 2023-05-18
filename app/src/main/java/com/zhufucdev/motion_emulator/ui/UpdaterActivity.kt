package com.zhufucdev.motion_emulator.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.zhufucdev.motion_emulator.SuspendedActivityResultLauncher
import com.zhufucdev.motion_emulator.FILE_PROVIDER_AUTHORITY
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.updater
import com.zhufucdev.update.UpdaterApp
import java.io.File

class UpdaterActivity : ComponentActivity() {
    private lateinit var activityResultLauncher: SuspendedActivityResultLauncher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityResultLauncher = SuspendedActivityResultLauncher(this)

        setContent {
            MotionEmulatorTheme {
                UpdaterApp(
                    navigateUp = { finish() },
                    updater = updater(),
                    install = {
                        installUpdate(it)
                    },
                )
            }
        }
    }

    private suspend fun installUpdate(file: File): Boolean {
        val permitted = requireInstallerPermission()
        if (!permitted) {
            return false
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                val fileUri = FileProvider.getUriForFile(this@UpdaterActivity, FILE_PROVIDER_AUTHORITY, file)
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            return false
        }

        return true
    }

    private suspend fun requireInstallerPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !packageManager.canRequestPackageInstalls()
        ) {
            activityResultLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
            return packageManager.canRequestPackageInstalls()
        }
        return true
    }
}
