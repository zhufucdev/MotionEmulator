package com.zhufucdev.motion_emulator.plugin

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zhufucdev.motion_emulator.extension.sharedPreferences

/**
 * The plug-in manager
 *
 * Should be initialized only by [com.zhufucdev.motion_emulator.MeApplication], which
 * means it's got global lifespan
 */
object Plugins {
    lateinit var available: List<Plugin>
        private set
    private lateinit var prefs: SharedPreferences
    fun init(context: Context) {
        prefs = context.sharedPreferences()
        available =
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter {
                    it.enabled && it.metaData?.getBoolean("me_plugin") == true
                }
                .map {
                    Plugin(
                        it.packageName,
                        context.packageManager.getApplicationLabel(it).toString(),
                        it.metaData.getString("me_description", "")
                    )
                }
        countEnabled = prefs.getString("plugins_enabled", "")!!.split(",").size
    }

    val enabled: List<Plugin>
        get() =
            prefs.getString("plugins_enabled", "")!!.split(",")
                .mapNotNull { saved -> available.firstOrNull { it.packageName == saved } }

    var countEnabled by mutableIntStateOf(0)
        private set

    fun setPriorities(enabled: List<Plugin>) {
        prefs.edit {
            putString("plugins_enabled", enabled.joinToString(",") { it.packageName })
        }
        countEnabled = enabled.size
    }

    fun notifyStart(context: Context) {
        enabled.forEach { it.notifyStart(context) }
    }

    fun notifyStop(context: Context) {
        enabled.forEach { it.notifyStop(context) }
    }

    fun notifySettingsChanged(context: Context) {
        enabled.forEach { it.notifySettingsChanged(context) }
    }
}