package com.zhufucdev.motion_emulator.ui.manager

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext

sealed class Screen<T : Referable>(
    val name: String,
    val titleId: Int,
    val iconId: Int,
    val screen: @Composable (ManagerViewModel<T>) -> Unit
) {
    object MotionScreen : Screen<Motion>(
        "motion",
        R.string.title_motion_data,
        R.drawable.ic_baseline_smartphone_24,
        { MotionScreen(it) }
    )

    object CellScreen : Screen<CellTimeline>(
        "cell",
        R.string.title_cells_data,
        R.drawable.ic_baseline_cell_tower_24,
        { CellScreen(it) }
    )

    object TraceScreen : Screen<Trace>(
        "trace",
        R.string.title_trace,
        R.drawable.ic_baseline_map_24,
        { TraceScreen(it) }
    )

    companion object {
        val list get() = listOf(MotionScreen, CellScreen, TraceScreen)
    }
}

sealed class ManagerViewModel<T : Referable> : ViewModel() {
    abstract val screen: Screen<T>
    abstract val data: SnapshotStateList<T>
    abstract fun onClick(item: T)
    abstract fun onRemove(item: T)

    lateinit var runtime: RuntimeArguments

    @Composable
    fun Compose(runtimeArguments: RuntimeArguments) {
        runtime = runtimeArguments
        screen.screen(this)
    }

    class MotionViewModel : DummyViewModel<Motion>(Screen.MotionScreen, Motions.list()) {
        override fun onClick(item: Motion) {

        }
    }

    open class DummyViewModel<T : Referable>(override val screen: Screen<T>, data: List<T>) :
        ManagerViewModel<T>() {
        private val coroutine by lazy { CoroutineScope(Dispatchers.Main) }

        override val data: SnapshotStateList<T> = data.toMutableStateList()

        override fun onClick(item: T) {}

        override fun onRemove(item: T) {
            val index = data.indexOf(item)
            data.remove(item)

            coroutine.launch {
                val action = runtime.snackbarHost.showSnackbar(
                    runtime.context.getString(R.string.text_deleted, item.id),
                    runtime.context.getString(R.string.action_undo)
                )
                if (action == SnackbarResult.ActionPerformed) {
                    undo(item, index)
                }
            }
        }

        private fun undo(item: T, index: Int) {
            data.add(index, item)
        }

        override fun onCleared() {
            super.onCleared()
            coroutine.cancel("cleared")
        }
    }

    data class RuntimeArguments(
        val snackbarHost: SnackbarHostState,
        val navigationController: NavController,
        val context: Context
    )
}
