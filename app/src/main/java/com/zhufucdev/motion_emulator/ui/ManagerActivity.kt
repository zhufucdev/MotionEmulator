package com.zhufucdev.motion_emulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.ui.manager.ManagerApp
import com.zhufucdev.motion_emulator.ui.manager.Screen
import com.zhufucdev.motion_emulator.ui.manager.ScreenParameter
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme

class ManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Motions.require(this)
        Cells.require(this)
        Traces.require(this)

        setContent {
            MotionEmulatorTheme {
                ManagerApp(navigateUp = { finish() }, dataProvider())
            }
        }
    }

    private fun dataProvider(): Map<Screen<*>, ScreenParameter<*>> {
        val motionHandler: (Motion) -> Unit = {

        }
        val cellHandler: (CellTimeline) -> Unit = {

        }
        val traceHandler: (Trace) -> Unit = {

        }
        return mapOf(
            Screen.MotionScreen to ScreenParameter(Motions.list(), motionHandler),
            Screen.CellScreen to ScreenParameter(Cells.list(), cellHandler),
            Screen.TraceScreen to ScreenParameter(Traces.list(), traceHandler)
        )
    }
}
