package com.zhufucdev.motion_emulator.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.highcapable.yukihookapi.YukiHookAPI
import com.zhufucdev.motion_emulator.lazySharedPreferences
import com.zhufucdev.motion_emulator.setUpStatusBar
import com.zhufucdev.motion_emulator.ui.home.AppHome
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class MainActivity : AppCompatActivity() {
    private var activated = mutableStateOf(false)
    private val preferences by lazySharedPreferences()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateStatus()
        setUpStatusBar()
        setContent {
            MotionEmulatorTheme {
                AppHome(activatedState = activated) {
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

    private fun updateStatus() {
        activated.value = YukiHookAPI.Status.isModuleActive
    }

    override fun onStart() {
        super.onStart()
        updateStatus()
    }
}