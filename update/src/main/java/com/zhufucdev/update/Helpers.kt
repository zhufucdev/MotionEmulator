package com.zhufucdev.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File


fun Context.installUpdate(file: File, fileProviderAuthority: String): Boolean {
    try {
        packageManager.packageInstaller
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            val fileUri =
                FileProvider.getUriForFile(this@installUpdate, fileProviderAuthority, file)
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    } catch (e: Exception) {
        Log.w("UpdaterHelper", "Unable to install ${file.name}", e)
        return false
    }

    return true
}

fun PackageManager.canInstallUpdate(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return true
    }
    return canRequestPackageInstalls()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun createForInstallerPermission(packageName: String) =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:$packageName")
    }

suspend fun ComponentActivity.requireInstallerPermission(launcher: SuspendedActivityResultLauncher): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
        launcher.launch(createForInstallerPermission(packageName))
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

class InstallationPermissionContract : ActivityResultContract<String, Boolean>() {
    private lateinit var pm: PackageManager
    override fun createIntent(context: Context, input: String): Intent {
        pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createForInstallerPermission(input)
        } else {
            throw NotImplementedError("Package installer permission control " +
                    "is not implemented before sdk 28")
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return pm.canInstallUpdate()
    }
}