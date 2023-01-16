package com.zhufucdev.motion_emulator.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun BottomSheetModal(
    modifier: Modifier = Modifier,
    state: BottomSheetModalState,
    content: @Composable () -> Unit
) {
    var height by remember { mutableStateOf(0F) }
    val density = LocalDensity.current
    fun targetValue() = if (state.open) 0F else height
    val animator = remember { Animatable(targetValue()) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(height) {
        if (height <= 0 && state.open) return@LaunchedEffect
        animator.snapTo(targetValue())
        delay(1.seconds) // idk why, just to solve shutter problems
        ready = true
    }

    LaunchedEffect(state.open) {
        animator.animateTo(targetValue())
    }

    Box(
        Modifier.fillMaxSize()
    ) {
        content()
        Backdrop(
            show = state.open,
            onClose = {
                state.open = false
            }
        )

        val drawer = state.drawerContent
        if (drawer != null) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .alpha(if (ready) 1F else 0F) // do not show when not measured
                        .offset(y = animator.value.dp)
                        .then(modifier),
                    shape = RoundedCornerShape(topStart = 16F, topEnd = 16F),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        Modifier
                            .onGloballyPositioned {
                                height = with(density) { it.size.height.toDp().value }
                            }
                    ) {
                        Box(
                            Modifier
                                .padding(top = paddingCommon, bottom = paddingSmall)
                                .size(width = 24.dp, height = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = RoundedCornerShape(8F)
                                )
                                .align(Alignment.CenterHorizontally)
                        )
                        drawer()
                    }
                }
            }
        }
    }
}

@Composable
fun Backdrop(show: Boolean, onClose: (() -> Unit)? = null) {
    val animator = remember { Animatable(if (show) 0.5F else 0F) }
    LaunchedEffect(show) {
        val target = if (show) 0.5F else 0F
        animator.animateTo(target)
    }
    if (animator.value > 0) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = animator.value))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onClose?.invoke()
                        }
                    )
                }
        )
    }
}

class BottomSheetModalState {
    var open: Boolean by mutableStateOf(false)
    var drawerContent: (@Composable () -> Unit)? by mutableStateOf(null)
        private set

    fun drawer(drawerContent: @Composable () -> Unit) {
        this.drawerContent = drawerContent
    }
}