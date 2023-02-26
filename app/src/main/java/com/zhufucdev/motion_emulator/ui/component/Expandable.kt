package com.zhufucdev.motion_emulator.ui.component

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.constraintlayout.compose.ConstraintLayout
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon
import com.zhufucdev.motion_emulator.ui.theme.paddingSmall

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Expandable(
    icon: @Composable () -> Unit,
    header: @Composable BoxScope.() -> Unit,
    body: @Composable BoxScope.() -> Unit,
    overview: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    var indicatorAnimation by remember { mutableStateOf(Animatable(0F)) }

    LaunchedEffect(expanded) {
        val targetValue = if (expanded) 180F else 0F
        if (indicatorAnimation.value == targetValue) return@LaunchedEffect

        indicatorAnimation = Animatable(180F - targetValue)
        indicatorAnimation.animateTo(targetValue)
    }

    Column(
        Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }
        ) {
            ConstraintLayout(
                Modifier.padding(
                    start = paddingCommon * 2,
                    end = paddingCommon * 2,
                    top = paddingCommon,
                    bottom = paddingCommon
                )
                    .fillMaxWidth()
            ) {
                val (s, h, o, i) = createRefs()
                Box(
                    Modifier
                        .padding(end = paddingSmall)
                        .constrainAs(s) {
                            start.linkTo(parent.start)
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                        }
                ) {
                    icon()
                }

                Box(
                    Modifier
                        .constrainAs(h) {
                            start.linkTo(s.end)
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                        }
                        .padding(start = paddingCommon)
                ) {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleMedium) {
                        header()
                    }
                }

                AnimatedContent(
                    targetState = expanded,
                    modifier = Modifier
                        .constrainAs(o) {
                            start.linkTo(h.end)
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                        }
                        .padding(start = paddingSmall)
                ) { e ->
                    if (!e) {
                        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelMedium) {
                            overview()
                        }
                    }
                }

                Box(
                    Modifier
                        .constrainAs(i) {
                            top.linkTo(parent.top)
                            bottom.linkTo(parent.bottom)
                            end.linkTo(parent.end)
                        }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_expand_more_24),
                        contentDescription = null,
                        modifier = Modifier
                            .rotate(indicatorAnimation.value)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = remember { expandVertically() },
            exit = remember { shrinkVertically() },
        ) {
            Box(
                Modifier.padding(
                    start = paddingCommon * 2,
                    end = paddingCommon * 2,
                    bottom = paddingCommon
                )
            ) {
                body()
            }
        }
    }
}