package com.zhufucdev.motion_emulator.plugin

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.zhufucdev.motion_emulator.sharedPreferences

/**
 * The plug-in manager
 *
 * Should be initialized only by [com.zhufucdev.motion_emulator.MeApplication], which
 * means it's got global lifespan
 */
object Plugins {
    private lateinit var available: List<Plugin>
    private lateinit var prefs: SharedPreferences
    fun init(context: Context) {
        prefs = context.sharedPreferences()
        available =
            context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter {
                    it.enabled && it.metaData?.getBoolean("me_plugin") == true
                }
                .map {
                    Plugin(it.packageName, it.name, it.metaData.getString("me_description", ""))
                }
    }

    val enabled: List<Plugin>
        get() =
            prefs.getString("plugins_enabled", "")!!.split(",")
                .mapNotNull { saved -> available.firstOrNull { it.packageName == saved } }

    fun setPriorities(list: List<Plugin>) {
        prefs.edit {
            putString("plugins_enabled", list.joinToString(",") { it.packageName })
        }
    }
}