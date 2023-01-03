package com.zhufucdev.motion_emulator.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment.Companion.CenterEnd
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import kotlin.math.abs
import kotlin.math.roundToInt
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
                        .align(CenterStart),
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
                        .align(CenterEnd),
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

@Composable
fun VerticalSpacer(space: Dp = paddingCommon) {
    Spacer(Modifier.height(space))
}

@Composable
fun HorizontalSpacer(space: Dp = paddingCommon) {
    Spacer(Modifier.width(space))
}

@Composable
fun CaptionText(modifier: Modifier = Modifier, text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
    )
}

private enum class SwipeableState {
    Hidden, Revealed, Filled
}

fun <T> Modifier.dragDroppable(
    element: T,
    list: MutableList<T>,
    state: LazyListState,
    itemsIgnored: Int = 0
): Modifier = composed {
    var offset by remember { mutableStateOf(0F) }
    var index by remember { mutableStateOf(-1) }
    var moved by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    then(
        Modifier.offset {
            IntOffset(x = 0, y = offset.roundToInt())
        }
            .zIndex(if (moving) 1F else 0F)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        index = list.indexOf(element)
                        offset = 0F
                        moving = true
                        moved = false
                    },
                    onDragEnd = {
                        offset = 0F
                        moving = false
                    },
                    onDrag = { change, dragAmount ->
                        if (dragAmount.y == 0F) return@detectDragGesturesAfterLongPress
                        offset += dragAmount.y

                        val info = state.layoutInfo.visibleItemsInfo
                        var target = index
                        var find = info[target + itemsIgnored].size
                        if (offset < 0) {
                            if (index <= 0) return@detectDragGesturesAfterLongPress
                            if (!moved) {
                                find = info[target + itemsIgnored - 1].size
                            }
                            while (find < -offset && target > 0) {
                                target--
                                find += info[target + itemsIgnored].size
                            }
                        } else {
                            if (index >= list.lastIndex) return@detectDragGesturesAfterLongPress
                            if (!moved) {
                                find = info[target + itemsIgnored + 1].size
                            }
                            while (find < offset && target < list.size) {
                                target++
                                find += info[target + itemsIgnored].size
                            }
                        }
                        if (target != index) {
                            list.removeAt(index)
                            list.add(target, element)
                            index = target
                            offset = 0F
                            moved = true
                        }
                    }
                )
            }
    )
}
