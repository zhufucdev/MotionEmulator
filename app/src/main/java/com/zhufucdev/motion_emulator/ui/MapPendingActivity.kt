package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zhufucdev.motion_emulator.ui.map.PendingApp
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class MapPendingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val targetActivity = intent.getStringExtra("target")
        val targetClass = classLoader.loadClass(targetActivity)
        setContent {
            MotionEmulatorTheme {
                PendingApp(
                    proceeding = targetClass,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MotionEmulatorTheme {
        PendingApp(
            proceeding = TraceDrawingActivity::class.java,
            onFinish = {}
        )
    }
}