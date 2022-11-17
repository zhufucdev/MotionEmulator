package com.zhufucdev.motion_emulator.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.zhufucdev.motion_emulator.isSystemApp
import io.ktor.utils.io.core.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import java.io.File
import kotlin.io.use

/**
 * Represents strategy applied to an app.
 *
 * @param positive in bypass mode, positive means not to hook
 */
data class AppMeta(val name: String?, val icon: Drawable?, val packageName: String, val positive: Boolean) {
    companion object {
        fun of(app: ApplicationInfo, pm: PackageManager, hooked: Boolean) =
            AppMeta(
                (app.labelRes.takeIf { it != 0 }?.let { pm.getText(app.packageName, it, app) })?.toString(),
                try {
                    pm.getApplicationIcon(app)
                } catch (_: Resources.NotFoundException) {
                    null
                },
                app.packageName,
                hooked
            )
    }
}

val AppMeta.hooked get() = (AppMetas.bypassMode && !positive) || (!AppMetas.bypassMode && positive)

@Serializable
data class AppStrategy(var showSystemApps: Boolean = false, var bypassMode: Boolean = true)

object AppMetas {
    private lateinit var pm: PackageManager
    private lateinit var infoStore: File
    private lateinit var configStore: File

    private val positiveApps = mutableSetOf<String>()
    private lateinit var config: AppStrategy

    var bypassMode: Boolean
        get() = config.bypassMode
        set(value) {
            config.bypassMode = value
            saveStrategy()
        }

    var showSystemApps: Boolean
        get() = config.showSystemApps
        set(value) {
            config.showSystemApps = value
            saveStrategy()
        }

    fun require(context: Context) {
        pm = context.packageManager
        val dataDir = context.getDir("apps", Context.MODE_PRIVATE)
        infoStore = File(dataDir, "infos.json")
        configStore = File(dataDir, "config.json")

        val record: List<String> =
            if (infoStore.exists()) {
                Json.decodeFromString(serializer(), infoStore.readText())
            } else {
                emptyList()
            }
        positiveApps.addAll(record)

        config = if (configStore.exists()) Json.decodeFromString(configStore.readText()) else AppStrategy()
    }

    fun list(): List<AppMeta> = buildList {
        pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { app ->
            if (!showSystemApps && app.isSystemApp) return@forEach

            val positive = positiveApps.contains(app.packageName)
            val meta = AppMeta.of(app, pm, positive)
            if (meta.name != null)
                add(meta)
        }
    }

    @Synchronized
    fun markPositive(packageName: String) {
        positiveApps.add(packageName)
        saveInfos()
    }

    @Synchronized
    fun markNegative(packageName: String) {
        positiveApps.remove(packageName)
        saveInfos()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun saveInfos() {
        infoStore.outputStream().use { stream ->
            Json.encodeToStream(positiveApps, stream)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun saveStrategy() {
        configStore.outputStream().use {
            Json.encodeToStream(config, it)
        }
    }
}
