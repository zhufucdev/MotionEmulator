package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Referable

sealed class ManagerViewModel<T : Referable> : ViewModel() {
    abstract val screen: Screen<T>
    abstract val data: SnapshotStateList<T>
    abstract fun onClick(item: T)
    abstract fun onRemove(item: T)
    abstract fun undo()

    fun parameter(): ScreenParameter<T> =
        ScreenParameter(screen, data, { onClick(it) }, { onRemove(it) })

    object MotionViewModel : ManagerViewModel<Motion>() {
        override val screen: Screen<Motion>
            get() = Screen.MotionScreen
        override val data: SnapshotStateList<Motion> = Motions.list().toMutableStateList()

        private var lastRemoved: Motion? = null

        override fun onClick(item: Motion) {

        }

        override fun onRemove(item: Motion) {
            data.remove(item)
            lastRemoved = item
        }

        override fun undo() {
            data.add(lastRemoved ?: return)
        }
    }

    companion object {
        val list get() = listOf(MotionViewModel)
    }
}
