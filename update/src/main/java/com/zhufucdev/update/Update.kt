package com.zhufucdev.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.DownloadManager.Query
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.runtime.*
import androidx.core.content.getSystemService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds

/**
 * Something checks and downloads app updates
 * @param apiUri See [this repo](https://github.com/zhufucdev/api.zhufucdev) to get an idea
 * @param productAlias How to call the app
 * @param context The context
 * @param exportedDir The directory where to [download]
 */
@Stable
class Updater(
    private val apiUri: String,
    private val productAlias: String,
    private val context: Context,
    private val exportedDir: File
) {
    private val ktor = HttpClient(Android) {
        engine {
            connectTimeout = 10_000
            socketTimeout = 10_000
        }

        install(ContentNegotiation) {
            json()
        }
    }

    var update: Update? by mutableStateOf(null)
        private set
    var status: UpdaterStatus by mutableStateOf(StatusIdling)
        private set

    private fun updateStatus(next: UpdaterStatus) {
        if (status == next) return

        try {
            status.onDestroy()
        } catch (e: Exception) {
            Log.w("Updater", "error while switching to next status: ${e.stackTraceToString()}")
        }
        status = next
    }

    /**
     * Look for a new update
     */
    suspend fun check(): Update? {
        updateStatus(StatusChecking)
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            val arch = Build.SUPPORTED_ABIS[0].standardArchitect()
            val req = ktor.get("${apiUri}/release?product=${productAlias}&current=${currentVersion}&arch=${arch}")
            if (!req.status.isSuccess()) {
                return null
            }
            return req.body<Update>().also { update = it }
        } finally {
            updateStatus(StatusReadyToDownload)
        }
    }

    /**
     * Download the update to the exported directory
     * @returns The downloaded file
     * @throws IllegalStateException if no update available
     * @throws RuntimeException if [DownloadManager] is not available
     */
    @SuppressLint("AutoboxingStateValueProperty")
    suspend fun download(): File {
        val update = this.update ?: throw IllegalStateException("update unavailable. Have you checked first?")
        val manager =
            context.getSystemService<DownloadManager>() ?: throw RuntimeException("download manager not available")

        updateStatus(StatusPreDownload)

        if (!exportedDir.exists()) exportedDir.mkdirs()
        val result = File(exportedDir, "${productAlias}-${update.name}.apk")
        if (result.exists()) {
            updateStatus(StatusReadyToInstall(result))
            return result // TODO Manifest verification
        }
        val taskId = manager.enqueue(DownloadManager.Request(Uri.parse(update.url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(result))
        })

        val coroutine = coroutineContext

        return suspendCoroutine { c ->
            val progress = mutableStateOf(0F)
            val status = StatusDownloading(progress, manager, taskId)

            thread(start = true) {
                while (this.status is HasUpdate) {
                    val query = queryDownload(manager, taskId)
                    Log.d("Updater", "query status $query")
                    if (query >= 1F) {
                        updateStatus(StatusReadyToInstall(result))
                        c.resumeWith(Result.success(result))
                        break
                    } else if (query == -2F) {
                        updateStatus(StatusDownloadFailed(result))
                        c.resumeWith(
                            Result.failure(
                                IllegalStateException(
                                    "download failed due to network failure, user " +
                                            "cancellation or alien attack"
                                )
                            )
                        )
                    } else if (query == -1F) {
                        updateStatus(StatusPreDownload)
                    } else {
                        updateStatus(status)
                        progress.value = query
                    }
                    Thread.sleep(1000)
                }
            }
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

    fun close() {
        ktor.close()
        status.onDestroy()
        update = null
    }

    private fun String.standardArchitect() = when (this) {
        "armeabi-v7a" -> "arm32"
        "arm64-v8a" -> "arm64"
        "x86_64" -> "amd64"
        else -> this
    }
}

@Serializable
data class Update(val name: String, val url: String)

interface UpdaterStatus {
    fun onDestroy()
}

interface StatusGenericWorking : UpdaterStatus

interface HasUpdate

interface Downloading : StatusGenericWorking, HasUpdate

object StatusChecking : StatusGenericWorking {
    override fun onDestroy() {}
}

object StatusPreDownload : StatusGenericWorking, HasUpdate {
    override fun onDestroy() {}
}

object StatusIdling : UpdaterStatus {
    override fun onDestroy() {}
}

object StatusReadyToDownload : UpdaterStatus, HasUpdate {
    override fun onDestroy() {}
}

data class StatusDownloading(
    var progress: MutableState<Float>,
    private val manager: DownloadManager,
    private val taskId: Long,
) : Downloading {
    override fun onDestroy() {
        manager.remove(taskId)
    }
}

data class StatusReadyToInstall(val file: File) : UpdaterStatus, HasUpdate {
    override fun onDestroy() {
        file.delete()
    }
}

data class StatusDownloadFailed(private val file: File): UpdaterStatus {
    override fun onDestroy() {
        if (file.exists())
            file.delete()
    }
}