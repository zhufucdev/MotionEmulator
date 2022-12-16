package com.zhufucdev.motion_emulator.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.highcapable.yukihookapi.YukiHookAPI
import com.zhufucdev.motion_emulator.ui.home.AppHome
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class MainActivity : AppCompatActivity() {
    private var activated = mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateStatus()
        setContent {
            MotionEmulatorTheme {
                AppHome(activatedState = activated) {
                    startActivity(Intent(this, it.activity))
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