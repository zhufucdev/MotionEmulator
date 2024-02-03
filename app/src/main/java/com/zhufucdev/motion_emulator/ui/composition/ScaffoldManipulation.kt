package com.zhufucdev.motion_emulator.ui.composition

import androidx.compose.runtime.Composable

@Composable
fun ScaffoldElements(manipulation: ScaffoldManipulationScope.() -> Unit) {
    val fabProvider = LocalFloatingActionButtonProvider.current
    manipulation(object : ScaffoldManipulationScope {
        override fun floatingActionButton(content: @Composable () -> Unit) {
            fabProvider.manipulator.composable(content)
        }

        override fun noFloatingButton() {
            fabProvider.manipulator.empty()
        }
    })
}

interface ScaffoldManipulationScope {
    fun floatingActionButton(content: @Composable () -> Unit)
    fun noFloatingButton()
}