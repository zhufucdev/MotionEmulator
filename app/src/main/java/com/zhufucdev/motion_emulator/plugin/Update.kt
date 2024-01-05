package com.zhufucdev.motion_emulator.plugin

import android.content.Context
import com.zhufucdev.sdk.ReleaseAsset
import com.zhufucdev.sdk.findAsset
import com.zhufucdev.motion_emulator.BuildConfig
import com.zhufucdev.update.AppUpdater
import com.zhufucdev.update.Updater
import com.zhufucdev.update.UpdaterStatus
import java.io.File

class PluginDownloader(
    private val productAlias: String,
    context: Context,
    exportedDir: File = File(context.externalCacheDir, "update")
) : Updater(context, exportedDir) {
    override suspend fun check(): ReleaseAsset? {
        updateStatus(UpdaterStatus.Working.Checking)
        val update = AppUpdater.checkForDevice(BuildConfig.server_uri, productAlias, ktor)
        this.update = update
        if (update != null) {
            updateStatus(UpdaterStatus.ReadyToDownload)
        } else {
            updateStatus(UpdaterStatus.Idling)
        }
        return update
    }
}

class PluginUpdater(
    private val plugin: Plugin,
    context: Context,
    private val resourceKey: String? = null,
    exportedDir: File = File(context.externalCacheDir, "update")
) : Updater(context, exportedDir) {
    override suspend fun check(): ReleaseAsset? {
        updateStatus(UpdaterStatus.Working.Checking)
        val version = context.packageManager.getPackageInfo(plugin.packageName, 0).versionName
        val key = resourceKey ?: run {
            val queries = ktor.findAsset(BuildConfig.server_uri, plugin.packageName)
            queries.firstOrNull()?.key ?: return null
        }

        val update = AppUpdater.checkForDevice(BuildConfig.server_uri, key, ktor, version)
        this.update = update
        updateStatus(UpdaterStatus.Idling)
        return update
    }
}
