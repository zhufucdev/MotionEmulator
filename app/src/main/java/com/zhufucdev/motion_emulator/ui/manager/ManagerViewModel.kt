package com.zhufucdev.motion_emulator.ui.manager

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zhufucdev.me.stub.CellTimeline
import com.zhufucdev.me.stub.Data
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.ui.component.BottomSheetModalState
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.extension.insert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class ManagerViewModel : ViewModel() {
    lateinit var runtime: RuntimeArguments
    abstract val screen: Screen
    abstract fun NavGraphBuilder.compose(runtimeArguments: RuntimeArguments)

    class OverviewViewModel : ManagerViewModel() {
        override val screen get() = Screen.OverviewScreen

        override fun NavGraphBuilder.compose(runtimeArguments: RuntimeArguments) {
            composable(screen.name) {
                runtime = runtimeArguments
                screen.List(this@OverviewViewModel)
            }
        }
    }

    data class RuntimeArguments(
        val snackbarHost: SnackbarHostState,
        val navigationController: NavController,
        val context: Context,
        val bottomModalState: BottomSheetModalState
    )
}

sealed class EditorViewModel<T : Data> : ManagerViewModel() {
    override val screen: Screen get() = editorScreen
    abstract val editorScreen: EditableScreen<T>
    abstract val data: SnapshotStateList<T>
    abstract fun onClick(item: T)
    abstract fun onRemove(item: T)
    abstract fun onModify(item: T)

    override fun NavGraphBuilder.compose(runtimeArguments: RuntimeArguments) {
        composable(screen.name) {
            runtime = runtimeArguments
            screen.List(this@EditorViewModel)
        }
        composable(
            route = "${screen.name}/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val target by remember { mutableStateOf(data.first { it.id == entry.arguments?.getString("id") }) }
            editorScreen.Editor(this@EditorViewModel, target)
        }
    }

    class MotionViewModel : StandardViewModel<Motion>(EditableScreen.MotionScreen, Motions)
    class CellsViewModel : StandardViewModel<CellTimeline>(EditableScreen.CellScreen, Cells)
    class TraceViewModel : StandardViewModel<Trace>(EditableScreen.TraceScreen, Traces)

    abstract class StandardViewModel<T : Data>(screen: EditableScreen<T>, val store: DataStore<T>) :
        DummyViewModel<T>(screen, store.list()) {
        override fun onRemove(item: T) {
            super.onRemove(item)
            store.delete(item, runtime.context)
        }

        override fun undo(item: T, index: Int) {
            super.undo(item, index)
            store.store(item)
        }

        override fun onModify(item: T) {
            super.onModify(item)
            store.store(item, true)
        }
    }

    open class DummyViewModel<T : Data>(override val editorScreen: EditableScreen<T>, data: List<T>) :
        EditorViewModel<T>() {
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
}

sealed class EditableScreen<T : Data>(
    name: String,
    titleId: Int,
    iconId: Int,
) : Screen(name, titleId, iconId) {
    @Composable
    override fun <M : ManagerViewModel> List(viewModel: M) {
        @Suppress("UNCHECKED_CAST")
        ListScreen(viewModel as EditorViewModel<T>)
    }

    @Composable
    abstract fun ListScreen(viewModel: EditorViewModel<T>)

    @Composable
    abstract fun Editor(viewModel: EditorViewModel<T>, target: T)

    object MotionScreen : EditableScreen<Motion>(
        "motion",
        R.string.title_motion,
        R.drawable.ic_baseline_smartphone_24
    ) {
        @Composable
        override fun ListScreen(viewModel: EditorViewModel<Motion>) {
            MotionScreen(viewModel)
        }

        @Composable
        override fun Editor(viewModel: EditorViewModel<Motion>, target: Motion) {
            MotionEditor(target, viewModel)
        }
    }

    object CellScreen : EditableScreen<CellTimeline>(
        "cell",
        R.string.title_cells,
        R.drawable.ic_baseline_cell_tower_24,
    ) {
        @Composable
        override fun ListScreen(viewModel: EditorViewModel<CellTimeline>) {
            CellScreen(viewModel)
        }

        @Composable
        override fun Editor(viewModel: EditorViewModel<CellTimeline>, target: CellTimeline) {
            CellEditor(target, viewModel)
        }
    }

    object TraceScreen : EditableScreen<Trace>(
        "trace",
        R.string.title_trace,
        R.drawable.ic_baseline_map_24,
    ) {
        @Composable
        override fun ListScreen(viewModel: EditorViewModel<Trace>) {
            TraceScreen(viewModel)
        }

        @Composable
        override fun Editor(viewModel: EditorViewModel<Trace>, target: Trace) {
            TraceEditor(target, viewModel)
        }
    }
}

sealed class Screen(
    val name: String,
    val titleId: Int,
    val iconId: Int,
) {
    @Composable
    abstract fun <M : ManagerViewModel> List(viewModel: M)

    object OverviewScreen :
        Screen(
            name = "overview",
            titleId = R.string.title_overview,
            iconId = R.drawable.ic_baseline_app_registration_24,
        ) {
        @Composable
        override fun <M : ManagerViewModel> List(viewModel: M) {
            OverviewScreen(viewModel)
        }
    }
}
