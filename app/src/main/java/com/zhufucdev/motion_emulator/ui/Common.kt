package com.zhufucdev.motion_emulator.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.secondary,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipHost(
    state: TooltipState = rememberTooltipState(), content: @Composable TooltipScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val coroutine = rememberCoroutineScope()
    val previousVisibleWindowFrame = remember { android.graphics.Rect() }

    val windowSize = previousVisibleWindowFrame.let {
        view.getWindowVisibleDisplayFrame(it)
        IntSize(it.width(), it.height())
    }

    content(object : TooltipScope {
        fun Density.calculatePosition(
            viewPos: Rect,
        ): Offset {
            val safeMargin = 24.dp.toPx()
            val contentMargin = paddingCommon.toPx()
            return if (viewPos.bottom >= windowSize.height + safeMargin) {
                Offset(viewPos.topCenter.x, viewPos.top + contentMargin)
            } else {
                Offset(viewPos.bottomCenter.x, viewPos.bottom + contentMargin)
            }
        }

        override fun Modifier.tooltip(content: @Composable () -> Unit): Modifier = composed {
            var position: Rect by remember { mutableStateOf(Rect.Zero) }

            onGloballyPositioned {
                position = it.boundsInWindow()
            }.pointerInput(true) {
                awaitEachGesture {
                    val timeout = viewConfiguration.longPressTimeoutMillis
                    val pass = PointerEventPass.Initial

                    awaitFirstDown(pass = pass)
                    try {
                        withTimeout(timeout) {
                            waitForUpOrCancellation(pass = pass)
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)


                        coroutine.launch {
                            state.show(calculatePosition(position), content)
                        }
                        awaitPointerEvent(pass = pass).changes.forEach { it.consume() }
                    }
                }
            }
        }
    })

    var contentWidth by remember { mutableIntStateOf(0) }
    if (state.isVisible) {
        state as TooltipStateImpl
        Surface(
            color = TooltipDefaults.plainTooltipContainerColor,
            shape = TooltipDefaults.plainTooltipContainerShape,
            modifier = Modifier
                .onSizeChanged {
                    contentWidth = it.width
                }
                .absoluteOffset {
                    state.position.let {
                        IntOffset(
                            min(
                                max(
                                    (it.x - contentWidth / 2f).roundToInt(),
                                    paddingCommon
                                        .toPx()
                                        .roundToInt()
                                ),
                                windowSize.width - contentWidth - paddingCommon
                                    .toPx()
                                    .roundToInt()
                            ), it.y.roundToInt()
                        )
                    }
                }
                .alpha(state.opacity)
        ) {
            CompositionLocalProvider(
                LocalContentColor provides TooltipDefaults.plainTooltipContentColor,
                LocalTextStyle provides MaterialTheme.typography.bodyMedium
            ) {
                Box(Modifier.padding(vertical = paddingSmall, horizontal = paddingCommon)) {
                    state.content?.invoke()
                }
            }
        }
    }
}

interface TooltipScope {
    fun Modifier.tooltip(content: @Composable () -> Unit): Modifier
}

@Stable
interface TooltipState {
    var isVisible: Boolean
    suspend fun show(position: Offset, content: @Composable () -> Unit)
    fun dismiss()
}

@Stable
private class TooltipStateImpl : TooltipState {
    override var isVisible: Boolean by mutableStateOf(false)
    var position: Offset by mutableStateOf(Offset.Zero)
    var opacity: Float by mutableFloatStateOf(0f)
    var content: (@Composable () -> Unit)? = null

    private var job: Job? = null

    override suspend fun show(position: Offset, content: @Composable () -> Unit) = coroutineScope {
        this@TooltipStateImpl.position = position
        this@TooltipStateImpl.content = content
        job?.cancel()
        job = launch {
            isVisible = true
            animate(opacity, 0.8f) { value, _ ->
                opacity = value
            }
        }

        launch {
            job?.join()
            delay(TooltipDuration)
            job = launch {
                animate(opacity, 0f, animationSpec = spring(2f)) { value, _ ->
                    opacity = value
                }
                isVisible = false
            }
        }

        Unit
    }

    override fun dismiss() {
        isVisible = false
        opacity = 0f
    }
}

@SuppressLint("ComposableNaming")
@Composable
fun rememberTooltipState(): TooltipState = remember { TooltipStateImpl() }

const val TooltipDuration = 1500L