package com.zhufucdev.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File


fun Context.installUpdate(file: File, fileProviderAuthority: String): Boolean {
    try {
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            val fileUri = FileProvider.getUriForFile(this@installUpdate, fileProviderAuthority, file)
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (e: Exception) {
        Log.w("UpdaterHelper", "unable to install ${file.name}")
        e.printStackTrace()
        return false
    }

    return true
}

suspend fun ComponentActivity.requireInstallerPermission(launcher: SuspendedActivityResultLauncher): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
        launcher.launch(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
        )
        return packageManager.canRequestPackageInstalls()
    }
    return true
}

class SuspendedActivityResultLauncher(activity: ComponentActivity) {
    private val callbacks = mutableListOf<(ActivityResult) -> Unit>()
    private val launcher: ActivityResultLauncher<Intent>

    init {
        launcher =
            activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                callbacks.forEach {
                    try {
                        it(result)
                    } catch (e: Exception) {
                        Log.w("ActivityResultLauncher", "error while handling a callback")
                        e.printStackTrace()
                    }
                }
                callbacks.clear()
            }
    }

    suspend fun launch(intent: Intent) = suspendCancellableCoroutine {
        launcher.launch(intent)
        callbacks.add { result ->
            it.resumeWith(Result.success(result))
        }
    }
}