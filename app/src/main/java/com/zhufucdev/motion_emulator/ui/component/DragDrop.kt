package com.zhufucdev.motion_emulator.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

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