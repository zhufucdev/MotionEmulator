package com.zhufucdev.motion_emulator.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun Swipeable(
    foreground: @Composable () -> Unit,
    fillColor: Color = MaterialTheme.colorScheme.onSurface,
    container: @Composable (@Composable () -> Unit) -> Unit,
    backgroundEnd: (@Composable RowScope.() -> Unit)? = null,
    backgroundStart: (@Composable RowScope.() -> Unit)? = null,
    endActivated: (() -> Unit)? = null,
    startActivated: (() -> Unit)? = null,
    fractionScale: Float = 0.5F,
    fractionWidth: Dp = 20.dp,
    revealedOffsetX: Dp = 80.dp
) {
    var state by remember { mutableStateOf(SwipeableState.Hidden) }
    var offsetX by remember { mutableStateOf(0F) }
    var rawOffsetGestureX by remember { mutableStateOf(0F) }
    var animator by remember { mutableStateOf(Animatable(Float.NaN)) }
    val targetOffsetPx = with(LocalDensity.current) { revealedOffsetX.toPx() }
    val gestureFix = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val effectiveOffset =
        with(LocalDensity.current) {
            (if (animator.isRunning) animator.value else offsetX).toDp()
        }

    var maxWidthPx by remember { mutableStateOf(0) }
    val localEndActivated by rememberUpdatedState(endActivated)
    val localStartActivated by rememberUpdatedState(startActivated)

    LaunchedEffect(key1 = animator) {
        if (animator.value.isNaN()) return@LaunchedEffect
        animator.animateTo(
            when (state) {
                SwipeableState.Hidden -> 0F
                SwipeableState.Revealed -> sign(offsetX) * targetOffsetPx
                SwipeableState.Filled -> sign(offsetX) * maxWidthPx
            }
        )
        offsetX = animator.targetValue
        rawOffsetGestureX = animator.targetValue
    }

    container {
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    maxWidthPx = it.size.width
                }
        ) {
            Box(Modifier.matchParentSize()) {
                Button(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(effectiveOffset.takeIf { it.value > 0 } ?: 0.dp)
                        .align(Alignment.CenterStart),
                    onClick = { localStartActivated?.invoke() },
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = fillColor)
                ) {
                    backgroundStart?.invoke(this)
                }

                Button(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(-(effectiveOffset.takeIf { it.value < 0 } ?: 0.dp))
                        .align(Alignment.CenterEnd),
                    onClick = { localEndActivated?.invoke() },
                    shape = MaterialTheme.shapes.extraSmall,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = fillColor)
                ) {
                    backgroundEnd?.invoke(this)
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = effectiveOffset)
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                state =
                                    if (abs(offsetX) in targetOffsetPx..(targetOffsetPx + fractionWidth.toPx())) {
                                        if (offsetX > 0 && startActivated != null || offsetX < 0 && endActivated != null)
                                            SwipeableState.Revealed
                                        else
                                            SwipeableState.Hidden
                                    } else if (abs(offsetX) > targetOffsetPx + fractionWidth.toPx()) {
                                        if (offsetX > 0) {
                                            localStartActivated?.let {
                                                it.invoke()
                                                SwipeableState.Filled
                                            }
                                        } else {
                                            localEndActivated?.let {
                                                it.invoke()
                                                SwipeableState.Filled
                                            }
                                        } ?: SwipeableState.Hidden
                                    } else {
                                        SwipeableState.Hidden
                                    }
                                animator = Animatable(offsetX)
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                if (offsetX != 0F
                                    || dragAmount > 0 && backgroundStart != null
                                    || dragAmount < 0 && backgroundEnd != null
                                ) {
                                    rawOffsetGestureX += dragAmount
                                    offsetX +=
                                        if (abs(rawOffsetGestureX) in targetOffsetPx..(targetOffsetPx + fractionWidth.toPx())) {
                                            dragAmount * fractionScale
                                        } else {
                                            dragAmount
                                        } * gestureFix
                                }
                            }
                        )
                    }
            ) {
                foreground()
            }
        }
    }
}

private enum class SwipeableState {
    Hidden, Revealed, Filled
}