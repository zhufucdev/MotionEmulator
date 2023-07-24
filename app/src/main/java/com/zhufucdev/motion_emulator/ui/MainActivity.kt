package com.zhufucdev.motion_emulator.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.zhufucdev.motion_emulator.extension.Updater
import com.zhufucdev.motion_emulator.extension.lazySharedPreferences
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.extension.setUpStatusBar
import com.zhufucdev.motion_emulator.ui.home.AppHome
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class MainActivity : AppCompatActivity() {
    private val preferences by lazySharedPreferences()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpStatusBar()

        setContent {
            MotionEmulatorTheme {
                val plugins = remember { Plugins.enabled.size }
                val updater = remember {
                    Updater(this)
                }
                LaunchedEffect(Unit) {
                    updater.check()
                }

                AppHome(updater = updater, enabledPlugins = plugins) {
                    val target = Intent(this, it.activity)
                    if (it.mapping && !preferences.contains("map_provider")) {
                        target.setClass(this, MapPendingActivity::class.java)
                        target.putExtra("target", it.activity.name)
                    }
                    startActivity(target)
                }
            }
        }
    }
}