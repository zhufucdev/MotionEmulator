package com.zhufucdev.motion_emulator.ui.manager

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.insert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class ManagerViewModel<T : Referable> : ViewModel() {
    abstract val screen: Screen<T>
    abstract val data: SnapshotStateList<T>
    abstract fun onClick(item: T)
    abstract fun onRemove(item: T)
    abstract fun onModify(item: T)

    lateinit var runtime: RuntimeArguments

    fun NavGraphBuilder.compose(runtimeArguments: RuntimeArguments) {
        composable(screen.name) {
            runtime = runtimeArguments
            screen.screen(this@ManagerViewModel)
        }

        composable(
            route = "${screen.name}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val target by remember { mutableStateOf(data.first { it.id == entry.arguments?.getString("id") }) }
            screen.editor(target, this@ManagerViewModel)
        }
    }

    class MotionViewModel : DummyViewModel<Motion>(Screen.MotionScreen, Motions.list()) {
        override fun onRemove(item: Motion) {
            super.onRemove(item)
            Motions.delete(item, runtime.context)
        }

        override fun undo(item: Motion, index: Int) {
            super.undo(item, index)
            Motions.store(item)
        }
    }

    class CellsViewModel : DummyViewModel<CellTimeline>(Screen.CellScreen, Cells.list()) {
        override fun onRemove(item: CellTimeline) {
            super.onRemove(item)
            Cells.delete(item, runtime.context)
        }

        override fun undo(item: CellTimeline, index: Int) {
            super.undo(item, index)
            Cells.store(item)
        }
    }

    class TraceViewModel : DummyViewModel<Trace>(Screen.TraceScreen, Traces.list()) {
        override fun onRemove(item: Trace) {
            super.onRemove(item)
            Traces.delete(item, runtime.context)
        }

        override fun undo(item: Trace, index: Int) {
            super.undo(item, index)
            Traces.store(item)
        }

        override fun onModify(item: Trace) {
            super.onModify(item)
            Traces.store(item, true)
        }
    }

    open class DummyViewModel<T : Referable>(override val screen: Screen<T>, data: List<T>) :
        ManagerViewModel<T>() {
        private val coroutine by lazy { CoroutineScope(Dispatchers.Main) }

        override val data: SnapshotStateList<T> = data.toMutableStateList()

        override fun onClick(item: T) {
            runtime.navigationController.navigate("${screen.name}/${item.id}")
        }

        override fun onRemove(item: T) {
            val index = data.indexOf(item)
            data.remove(item)

            coroutine.launch {
                val action = runtime.snackbarHost.showSnackbar(
                    message = runtime.context.getString(R.string.text_deleted, item.id),
                    actionLabel = runtime.context.getString(R.string.action_undo),
                    withDismissAction = true
                )
                if (action == SnackbarResult.ActionPerformed) {
                    undo(item, index)
                }
            }
        }

        open fun undo(item: T, index: Int) {
            data.insert(index, item)
        }

        override fun onModify(item: T) {
            val index = data.indexOfFirst { it.id == item.id }
            data[index] = item
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

sealed class Screen<T : Referable>(
    val name: String,
    val titleId: Int,
    val iconId: Int,
    val screen: @Composable (ManagerViewModel<T>) -> Unit,
    val editor: @Composable (T, ManagerViewModel<T>) -> Unit
) {
    object MotionScreen : Screen<Motion>(
        "motion",
        R.string.title_motion_data,
        R.drawable.ic_baseline_smartphone_24,
        { MotionScreen(it) },
        { m, m2 -> MotionEditor(m, m2) }
    )

    object CellScreen : Screen<CellTimeline>(
        "cell",
        R.string.title_cells_data,
        R.drawable.ic_baseline_cell_tower_24,
        { CellScreen(it) },
        { c, m -> CellEditor(c, m) }
    )

    object TraceScreen : Screen<Trace>(
        "trace",
        R.string.title_trace,
        R.drawable.ic_baseline_map_24,
        { TraceScreen(it) },
        { t, m -> TraceEditor(t, m) }
    )

    companion object {
        val list get() = listOf(MotionScreen, CellScreen, TraceScreen)
    }
}

