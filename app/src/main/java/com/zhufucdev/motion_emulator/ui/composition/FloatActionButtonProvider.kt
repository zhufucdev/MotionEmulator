package com.zhufucdev.motion_emulator.ui.composition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp

data class FloatActionButtonProvider(val manipulator: FloatingActionButtonManipulator)

val LocalFloatingActionButtonProvider =
    compositionLocalOf { FloatActionButtonProvider(DefaultFloatingActionButtonManipulator) }

interface FloatingActionButtonManipulator {
    fun composable(content: @Composable () -> Unit)
    fun empty()
}

object DefaultFloatingActionButtonManipulator : FloatingActionButtonManipulator {
    private var currentFab by mutableStateOf<(@Composable () -> Unit)?>(null)
    override fun composable(content: @Composable () -> Unit) {
        currentFab = content
    }

    override fun empty() {
        currentFab = null
    }

    @Composable
    fun CurrentFloatingActionButton() {
        AnimatedContent(
            targetState = currentFab,
            label = "Default Floating Action Button",
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = scaleIn(),
                    initialContentExit = scaleOut(),
                    sizeTransform = SizeTransform(clip = false)
                )
            }
        ) {
            it?.invoke()
        }
    }
}