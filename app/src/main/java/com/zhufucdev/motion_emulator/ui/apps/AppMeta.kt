package com.zhufucdev.motion_emulator.ui.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiHookBridge
import com.highcapable.yukihookapi.hook.xposed.prefs.YukiHookModulePrefs
import com.zhufucdev.motion_emulator.BuildConfig
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

fun PackageParam.isHooked(): Boolean {
    if (packageName == BuildConfig.APPLICATION_ID) return false

    val positiveApps = prefs.getStringSet("positive_apps", emptySet())
    val bypassMode = prefs.getBoolean("bypass", true)
    val showSystemApps = prefs.getBoolean("use_system")

    if (!bypassMode) {
        return positiveApps.contains(packageName)
    }

    if (!showSystemApps && appInfo.isSystemApp) {
        return false
    }

    return !positiveApps.contains(packageName)
}

@Serializable
data class AppStrategy(var showSystemApps: Boolean = false, var bypassMode: Boolean = true)

object AppMetas {
    private lateinit var pm: PackageManager
    private lateinit var prefs: YukiHookModulePrefs

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
        prefs = context.modulePrefs
        pm = context.packageManager

        val record = prefs.getStringSet("positive_apps", emptySet())
        config = AppStrategy(
            showSystemApps = prefs.getBoolean("use_system"),
            bypassMode = prefs.getBoolean("bypass", true)
        )
        positiveApps.addAll(record)
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

    private fun saveInfos() {
        prefs.putStringSet("positive_apps", positiveApps)
    }

    private fun saveStrategy() {
        prefs.putBoolean("use_system", showSystemApps)
        prefs.putBoolean("bypass", bypassMode)
    }
}
