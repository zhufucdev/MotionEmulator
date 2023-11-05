package com.zhufucdev.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.DownloadManager.Query
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import com.zhufucdev.api.ReleaseAsset
import com.zhufucdev.api.getReleaseAsset
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.suspendCoroutine

/**
 * Something checks and downloads app updates
 *
 * This is also a view model
 *
 * @param context The context
 * @param exportedDir The directory where to [download]
 */
@Stable
abstract class Updater(
    protected val context: Context,
    private val exportedDir: File = File(context.externalCacheDir, "update")
) {
    protected val ktor = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
        }

        install(ContentNegotiation) {
            json()
        }
    }

    open var update: ReleaseAsset? by mutableStateOf(null)
        protected set
    var status: UpdaterStatus by mutableStateOf(UpdaterStatus.Idling)
        private set

    protected fun updateStatus(next: UpdaterStatus) {
        if (status == next) return

        try {
            status.onDestroy()
        } catch (e: Exception) {
            Log.w("Updater", "error while switching to next status: ${e.stackTraceToString()}")
        }
        status = next
    }

    /**
     * Check for new update, or null if already up-to-date
     *
     * Note: this method mutates the global [update]
     */
    abstract suspend fun check(): ReleaseAsset?

    /**
     * Download the update to the exported directory
     * @param update Override the global [Updater.update]
     * @returns The downloaded file
     * @throws IllegalStateException if no update available
     * @throws RuntimeException if [DownloadManager] is not available
     */
    @SuppressLint("AutoboxingStateValueProperty")
    suspend fun download(update: ReleaseAsset? = null): File {
        val localUpdate = update ?: this.update
        ?: throw IllegalStateException("update unavailable. Have you checked first?")
        val manager =
            context.getSystemService<DownloadManager>()
                ?: throw RuntimeException("download manager not available")

        updateStatus(UpdaterStatus.Working.PreDownload)

        if (!exportedDir.exists()) exportedDir.mkdirs()
        val result = File(exportedDir, "${localUpdate.productName}-${localUpdate.versionName}.apk")
        if (result.exists()) {
            updateStatus(UpdaterStatus.ReadyToInstall(result))
            return result // TODO Manifest verification
        }
        val taskId = manager.enqueue(DownloadManager.Request(Uri.parse(localUpdate.url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationUri(Uri.fromFile(result))
        })

        return suspendCoroutine { c ->
            val progress = mutableFloatStateOf(0F)
            val status = UpdaterStatus.Working.Downloading(progress, manager, taskId)

            thread(start = true) {
                while (this.status is UpdaterStatus.HasUpdate) {
                    val query = queryDownload(manager, taskId)
                    Log.d("Updater", "query status $query")
                    if (query >= 1F) {
                        updateStatus(UpdaterStatus.ReadyToInstall(result))
                        c.resumeWith(Result.success(result))
                        return@thread
                    } else if (query == -2F) {
                        updateStatus(UpdaterStatus.DownloadFailed(result))
                        c.resumeWith(
                            Result.failure(
                                IllegalStateException(
                                    "download failed due to network failure, user " +
                                            "cancellation or alien attack"
                                )
                            )
                        )
                    } else if (query == -1F) {
                        updateStatus(UpdaterStatus.Working.PreDownload)
                    } else {
                        updateStatus(status)
                        progress.value = query
                    }
                    Thread.sleep(1000)
                }
            }
        }
    }

    fun close() {
        ktor.close()
        status.onDestroy()
        update = null
    }
}

@SuppressLint("Range")
private fun queryDownload(
    manager: DownloadManager,
    taskId: Long
): Float {
    val query = Query().apply { setFilterById(taskId) }
    val cursor = manager.query(query)
    if (!cursor.moveToFirst()) {
        cursor.close()
        return -2F
    }

    val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
    if (statusCol < 0) return 0F
    when (cursor.getInt(statusCol)) {
        DownloadManager.STATUS_SUCCESSFUL -> {
            return 1F
        }

        DownloadManager.STATUS_FAILED -> {
            return -2F
        }

        DownloadManager.STATUS_RUNNING -> {
            val downloaded =
                cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total =
                cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return downloaded * 1F / total
        }
    }

    cursor.close()
    return -1F
}

/**
 * An [Updater] to update the running app itself
 *
 * @param apiUri See [this repo](https://github.com/zhufucdev/api.zhufucdev) to get an idea
 */
class AppUpdater(
    private val apiUri: String,
    private val productAlias: String,
    context: Context,
    exportedDir: File = File(context.externalCacheDir, "update")
) : Updater(context, exportedDir) {
    /**
     * Look for a new update, with the
     * [android.content.pm.PackageInfo.versionName] of the current [context] as default version
     */
    override suspend fun check(): ReleaseAsset? {
        updateStatus(UpdaterStatus.Working.Checking)
        val update = checkForDevice(
            apiUri,
            productAlias,
            ktor,
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        )
        if (update != null) {
            this.update = update
            updateStatus(UpdaterStatus.ReadyToDownload)
        } else {
            updateStatus(UpdaterStatus.Idling)
        }
        return update
    }

    companion object {
        suspend fun checkForDevice(
            apiUri: String,
            productAlias: String,
            ktor: HttpClient,
            version: String? = null
        ): ReleaseAsset? {
            val arch = when (val a = Build.SUPPORTED_ABIS[0]) {
                "armeabi-v7a" -> "arm32"
                "arm64-v8a" -> "arm64"
                "x86_64" -> "amd64"
                else -> a
            }
            return ktor.getReleaseAsset(apiUri, productAlias, "android", version, arch)
        }
    }
}

class AssetUpdater(
    asset: ReleaseAsset?,
    context: Context,
    exportedDir: File = File(context.externalCacheDir, "update")
) : Updater(context, exportedDir) {
    @Stable
    override var update: ReleaseAsset? = asset

    override suspend fun check(): ReleaseAsset? {
        return update
    }
}

sealed class UpdaterStatus {
    abstract fun onDestroy()
    interface HasUpdate

    sealed class Working : UpdaterStatus() {
        @Stable
        class Downloading(
            progress: MutableFloatState,
            private val manager: DownloadManager,
            private val taskId: Long,
        ) : Working(), HasUpdate {
            val progress by progress
            override fun onDestroy() {
                if (queryDownload(manager, taskId) < 1) {
                    manager.remove(taskId)
                }
            }
        }

        data object Checking : Working() {
            override fun onDestroy() {}
        }

        data object PreDownload : Working(), HasUpdate {
            override fun onDestroy() {}
        }
    }

    data object Idling : UpdaterStatus() {
        override fun onDestroy() {}
    }

    object ReadyToDownload : UpdaterStatus(), HasUpdate {
        override fun onDestroy() {}
    }

    data class ReadyToInstall(val file: File) : UpdaterStatus(), HasUpdate {
        override fun onDestroy() {
            file.delete()
        }
    }

    data class DownloadFailed(private val file: File) : UpdaterStatus() {
        override fun onDestroy() {
            if (file.exists())
                file.delete()
        }
    }
}






