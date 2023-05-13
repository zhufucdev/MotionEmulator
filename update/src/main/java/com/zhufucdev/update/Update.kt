package com.zhufucdev.update

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.DownloadManager.Query
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds

/**
 * Something checks and downloads app updates
 * @param apiUri See [this repo](https://github.com/zhufucdev/api.zhufucdev) to get an idea
 * @param productAlias How to call the app
 * @param context The context
 */
@Stable
class Updater(private val apiUri: String, private val productAlias: String, private val context: Context) {
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
    var progress: Float by mutableStateOf(-1F)
        private set
    var downloading: Boolean by mutableStateOf(false)
        private set
    var checking: Boolean by mutableStateOf(false)
        private set

    suspend fun check(): Update? {
        checking = true
        try {
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            val arch = Build.SUPPORTED_ABIS[0].standardArchitect()
            val req = ktor.get("${apiUri}/release?product=${productAlias}&current=${currentVersion}&arch=${arch}")
            if (!req.status.isSuccess()) {
                return null
            }
            return req.body<Update>().also { update = it }
        } finally {
            checking = false
        }
    }

    suspend fun download(): File {
        val update = this.update ?: throw IllegalStateException("update unavailable. Have you checked first?")

        val manager =
            context.getSystemService<DownloadManager>() ?: throw RuntimeException("download manager not available")

        val result =
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${productAlias}-${update.name}.apk")
        val taskId = manager.enqueue(DownloadManager.Request(Uri.parse(update.url)).apply {
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, result.name)
        })
        downloading = true

        val coroutineContext = coroutineContext
        return suspendCoroutine { c ->
            CoroutineScope(coroutineContext).launch {
                while (true) {
                    val progress = queryDownload(manager, taskId)
                    if (progress >= 1F) {
                        c.resumeWith(Result.success(result))
                        break
                    } else if (progress == -2F) {
                        c.resumeWith(
                            Result.failure(
                                IllegalStateException(
                                    "download failed due to network failure, user " +
                                            "cancellation or alien attack"
                                )
                            )
                        )
                    } else {
                        this@Updater.progress = progress
                    }
                    delay(0.5.seconds)
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
            return 0F
        }

        val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (statusCol < 0) return 0F
        when (cursor.getInt(statusCol)) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                downloading = false
                return 1F
            }

            DownloadManager.STATUS_FAILED -> {
                downloading = false
                return -2F
            }

            DownloadManager.STATUS_RUNNING -> {
                downloading = true
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