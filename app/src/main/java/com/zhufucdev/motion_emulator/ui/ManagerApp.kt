@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhufucdev.me.stub.CellTimeline
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Telephonies
import com.zhufucdev.motion_emulator.data.DataLoader
import com.zhufucdev.motion_emulator.data.Motions
import com.zhufucdev.motion_emulator.data.Traces
import com.zhufucdev.motion_emulator.extension.dateString
import com.zhufucdev.motion_emulator.extension.displayName
import com.zhufucdev.motion_emulator.extension.effectiveTimeFormat
import com.zhufucdev.motion_emulator.ui.component.CaptionText
import com.zhufucdev.motion_emulator.ui.component.Expandable
import com.zhufucdev.motion_emulator.ui.component.HorizontalSpacer
import com.zhufucdev.motion_emulator.ui.component.Swipeable
import com.zhufucdev.motion_emulator.ui.component.TooltipHost
import com.zhufucdev.motion_emulator.ui.component.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.composition.LocalNavControllerProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalNestedScrollConnectionProvider
import com.zhufucdev.motion_emulator.ui.composition.LocalSnackbarProvider
import com.zhufucdev.motion_emulator.ui.composition.ScaffoldElements
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import com.zhufucdev.motion_emulator.ui.theme.MotionEmulatorTheme
import com.zhufucdev.motion_emulator.ui.theme.PaddingCommon
import com.zhufucdev.motion_emulator.ui.theme.PaddingLarge
import com.zhufucdev.motion_emulator.ui.theme.PaddingSmall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Composable
fun ManagerApp(
    paddingValues: PaddingValues,
    managerModel: ManagerViewModel = viewModel()
) {
    var actionTransition by remember {
        mutableFloatStateOf(0f)
    }
    var showActions by remember {
        mutableStateOf(false)
    }
    ScaffoldElements {
        floatingActionButton {
            val rotation by remember {
                derivedStateOf {
                    actionTransition * 135f
                }
            }
            val translation by remember {
                derivedStateOf {
                    (1 - actionTransition) * 24f
                }
            }
            var expanded by remember { mutableStateOf(false) }
            LaunchedEffect(expanded) {
                if (expanded) {
                    showActions = true
                }
                animate(
                    initialValue = actionTransition,
                    targetValue = if (expanded) 1f else 0f
                ) { v, _ ->
                    actionTransition = v
                }
                if (!expanded) {
                    showActions = false
                }
            }
            FloatingActionButton(
                content = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "",
                        modifier = Modifier.rotate(rotation)
                    )
                },
                onClick = { expanded = !expanded }
            )

            if (showActions) {
                Popup(
                    alignment = Alignment.BottomEnd,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    Column(modifier = Modifier, horizontalAlignment = Alignment.End) {
                        managerModel.stores.forEachIndexed { index, it ->
                            VerticalSpacer()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier
                                    .offset(y = ((managerModel.stores.size - index - 1) * translation).dp)
                                    .alpha(actionTransition)
                            ) {
                                Text(
                                    text = stringResource(id = nameIdByStore[it]!!),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                HorizontalSpacer()
                                FloatingActionButton(
                                    onClick = {

                                    },
                                    content = {
                                        Icon(
                                            imageVector = iconByStore[it]!!,
                                            contentDescription = null
                                        )
                                    },
                                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                    modifier = Modifier.rotate(if (index == managerModel.stores.lastIndex) rotation - 135f else 0f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    TooltipHost {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(1 - actionTransition * 0.7f)
                .padding(paddingValues)
                .then(
                    LocalNestedScrollConnectionProvider.current
                        ?.let { Modifier.nestedScroll(it) }
                        ?: Modifier
                ),
        ) {
            OverviewScreen(managerModel)
        }
    }
}

@Composable
@Preview
fun ActivityPreview() {
    MotionEmulatorTheme {
        ManagerApp(
            paddingValues = PaddingValues(0.dp),
            managerModel = ManagerViewModel(
                context = LocalContext.current, stores = listOf()
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverviewScreen(viewModel: ManagerViewModel = viewModel()) {
    val coroutine = rememberCoroutineScope()
    val context = LocalContext.current
    var operation by remember { mutableStateOf(ExportType.File) }
    var exporting by remember { mutableStateOf(mapOf<String, List<DataLoader<*>>>()) }
    var openBottomModal by remember { mutableStateOf(false) }
    val formatter = remember { context.effectiveTimeFormat() }
    val snackbars = LocalSnackbarProvider.current
    val dataReady by viewModel.dataLoader.collectAsState(initial = false)

    val fileCreateHintLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == Activity.RESULT_OK && uri != null) {
                coroutine.launch(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        viewModel.writeInto(out, exporting)
                    }
                }
            }
        }

    val fileOpenHintLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val uri = it.data?.data
            if (it.resultCode == Activity.RESULT_OK && uri != null) {
                coroutine.launch {
                    val count = viewModel.import(uri)
                    snackbars?.showSnackbar(
                        message = context.getString(R.string.text_imported, count)
                    )
                }
            }
        }

    if (openBottomModal) {
        DisposableEffect(true) {
            onDispose {
                viewModel.exportedDir().deleteRecursively() // clear shared file
            }
        }

        ModalBottomSheet(onDismissRequest = { openBottomModal = false }) {
            SheetContent(
                viewModel = viewModel,
                onClick = {
                    when (operation) {
                        ExportType.File -> {
                            exporting = it
                            fileCreateHintLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/gzip"
                                    putExtra(
                                        Intent.EXTRA_TITLE,
                                        "${
                                            context.getString(
                                                R.string.title_exported,
                                                formatter.dateString()
                                            )
                                        }.tar.gz"
                                    )
                                }
                            )
                        }

                        ExportType.Share -> {
                            coroutine.launch(Dispatchers.IO) {
                                val uri = viewModel.getExportedUri(it)
                                val share = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    type = "application/gzip"
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        share,
                                        context.getString(R.string.title_send_to)
                                    )
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    LazyColumn {
        // Meta operations
        item {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.title_export)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_database_export),
                        contentDescription = null
                    )
                },
                onClick = {
                    operation = ExportType.File
                    openBottomModal = true
                }
            )
        }

        item {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.title_import)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_database_import),
                        contentDescription = null
                    )
                },
                onClick = {
                    fileOpenHintLauncher.launch(
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/gzip"
                        }
                    )
                }
            )
        }

        item {
            ListItem(
                modifier = Modifier.fillMaxWidth(),
                title = { Text(stringResource(R.string.title_share)) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_baseline_share_24),
                        contentDescription = null
                    )
                },
                onClick = {
                    operation = ExportType.Share
                    openBottomModal = true
                },
                divider = false
            )
        }


        if (!dataReady) {
            item {
                VerticalSpacer(PaddingLarge)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    VerticalSpacer()
                    Text(text = stringResource(id = R.string.des_loading_data))
                }
            }
        }

        if (viewModel.data.isNotEmpty()) {
            // Data list
            item {
                CaptionText(
                    text = stringResource(id = R.string.title_data),
                    modifier = Modifier.padding(PaddingCommon)
                )
            }

            viewModel.data.forEachIndexed { index, item ->
                item(item.id) {
                    var heightAnimator by remember { mutableStateOf(Animatable(0F)) }
                    var removed by remember { mutableStateOf(false) }
                    val displayName = remember {
                        item.metadata.displayName(context)
                    }

                    LaunchedEffect(removed) {
                        if (!removed) return@LaunchedEffect

                        heightAnimator.animateTo(0F)
                        viewModel.data.remove(item)
                        viewModel.remove(item)

                        coroutine.launch {
                            val res = snackbars?.showSnackbar(
                                message = context.getString(R.string.text_deleted, displayName),
                                actionLabel = context.getString(R.string.action_undo)
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                viewModel.data.add(item)
                                viewModel.data.sortBy { it.id }
                                viewModel.save(item)
                            }
                        }
                    }

                    Swipeable(
                        foreground = {
                            val navController = LocalNavControllerProvider.current
                            val store = viewModel.storeByClass[item.clazz]!!
                            ListItem(
                                title = { Text(displayName) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = iconByStore[store]!!,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    navController?.navigate("${store.typeName}/${item.id}")
                                },
                                divider = index != viewModel.data.lastIndex,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        fillColor = MaterialTheme.colorScheme.errorContainer,
                        backgroundEnd = {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_delete_24),
                                contentDescription = "delete",
                            )
                        },
                        container = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        max =
                                        if (!removed) Dp.Infinity
                                        else with(LocalDensity.current) { heightAnimator.value.toDp() }
                                    )
                                    .onGloballyPositioned {
                                        if (!removed) heightAnimator =
                                            Animatable(it.size.height.toFloat())
                                    },
                                content = {
                                    it()
                                }
                            )
                        },
                        rearActivated = { removed = true },
                        fractionWidth = 50.dp
                    )
                }
            }
        }
    }
}

private enum class ExportType {
    File, Share
}

@Composable
private fun SheetContent(
    viewModel: ManagerViewModel = viewModel(),
    onClick: (Map<String, List<DataLoader<*>>>) -> Unit
) {
    val context = LocalContext.current
    val items = remember {
        viewModel.stores.associateWith { store ->
            viewModel.data.filter { it::class == store.clazz }.toMutableStateList()
        }
    }

    LazyColumn {
        item(key = "header") {
            Text(
                text = stringResource(R.string.title_select_to_export),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(
                    start = PaddingCommon * 2,
                    top = PaddingSmall,
                    bottom = PaddingCommon
                )
            )
        }

        viewModel.stores.forEach { store ->
            item(key = store.typeName) {
                var expanded by remember { mutableStateOf(false) }
                Expandable(
                    icon = {
                        Icon(imageVector = iconByStore[store]!!, contentDescription = null)
                    },
                    header = {
                        Text(stringResource(nameIdByStore[store]!!))
                    },
                    body = {
                        val targetList = items[store]!!
                        val data = remember { targetList.toList() }
                        if (data.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth()) {
                                data.forEachIndexed { index, datum ->
                                    var selected by remember { mutableStateOf(true) }
                                    SelectableItem(
                                        title = datum.metadata.displayName(context),
                                        subtitle = datum.id,
                                        selected = selected,
                                        onSelectedChanged = { s ->
                                            selected = s
                                            if (s) {
                                                targetList.add(datum)
                                            } else {
                                                targetList.remove(datum)
                                            }
                                        },
                                        divider = index != data.lastIndex
                                    )
                                }
                            }
                        } else {
                            Image(
                                painterResource(R.drawable.ic_thinking_face_72),
                                contentDescription = stringResource(R.string.text_empty_list),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    overview = {
                        val count = items[store]!!.size
                        Text(stringResource(R.string.text_selected_items, count))
                    },
                    expanded = expanded,
                    onToggle = { expanded = !expanded }
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PaddingCommon),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        onClick(items.entries.associate { (store, data) -> store.typeName to data.toList() })
                    },
                    content = {
                        Text(stringResource(R.string.action_continue))
                    }
                )
            }
        }

        item {
            VerticalSpacer()
        }
    }
}

@Composable
private fun LazyItemScope.SelectableItem(
    title: String,
    subtitle: String,
    selected: Boolean,
    divider: Boolean = true,
    onSelectedChanged: (Boolean) -> Unit
) {
    Column(Modifier.fillParentMaxWidth()) {
        ConstraintLayout(
            modifier = Modifier
                .clickable { onSelectedChanged(!selected) }
                .padding(start = PaddingCommon * 2, top = PaddingCommon, bottom = PaddingCommon)
                .fillMaxWidth(),
        ) {
            val (content, box) = createRefs()
            Column(
                Modifier.constrainAs(content) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    bottom.linkTo(parent.bottom)
                }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Start
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                )
            }


            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChanged,
                modifier = Modifier
                    .constrainAs(box) {
                        top.linkTo(parent.top)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                    }
                    .padding(end = PaddingCommon)
            )
        }

        if (divider) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5F)
            )
        }
    }
}

@Composable
fun CellEditor(
    target: DataLoader<CellTimeline>,
    paddingValues: PaddingValues,
    viewModel: ManagerViewModel = viewModel()
) {
    ScaffoldElements {
        noFloatingButton()
    }

    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()

    Box(
        Modifier
            .padding(PaddingCommon)
            .padding(paddingValues)
    ) {
        BasicEdit(
            id = target.id,
            name = target.metadata.displayName(context),
            onNameChanged = {
                val newValue = target.copy(metadata = target.metadata.copy(name = it))
                viewModel.update(newValue)
                coroutine.launch {
                    viewModel.save(newValue)
                }
            },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_baseline_cell_tower_24),
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
fun MotionEditor(
    target: DataLoader<Motion>,
    paddingValues: PaddingValues,
    viewModel: ManagerViewModel = viewModel()
) {
    ScaffoldElements {
        noFloatingButton()
    }

    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()

    Box(
        Modifier
            .padding(PaddingCommon)
            .padding(paddingValues)
    ) {
        BasicEdit(
            id = target.id,
            name = target.metadata.displayName(context),
            onNameChanged = {
                val newValue = target.copy(metadata = target.metadata.copy(name = it))
                viewModel.update(newValue)
                coroutine.launch {
                    viewModel.save(newValue)
                }
            },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_baseline_smartphone_24),
                    contentDescription = null
                )
            }
        )
    }
}

fun LazyListScope.basicEditItems(
    id: String,
    name: String,
    onNameChanged: (String) -> Unit,
    bottomMargin: Dp = PaddingCommon * 2,
    icon: @Composable () -> Unit
) {
    item {
        TextField(
            label = { Text(stringResource(id = R.string.title_id)) },
            value = id,
            onValueChange = {},
            leadingIcon = icon,
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        VerticalSpacer()
    }

    item {
        TextField(
            label = { Text(stringResource(id = R.string.title_name)) },
            value = name,
            onValueChange = onNameChanged,
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_edit_24),
                    contentDescription = null
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        VerticalSpacer(bottomMargin)
    }
}

@Composable
fun BasicEdit(
    id: String,
    name: String,
    onNameChanged: (String) -> Unit,
    icon: @Composable () -> Unit
) {
    var rename by remember { mutableStateOf(name) }
    var committed by remember { mutableStateOf(false) }
    LaunchedEffect(rename) {
        val captured = rename
        committed = false
        delay(1.seconds)

        if (rename != captured) return@LaunchedEffect
        onNameChanged(captured)
        committed = true
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!committed && rename != name) {
                onNameChanged(rename)
            }
        }
    }

    LazyColumn {
        basicEditItems(
            id = id,
            name = rename,
            onNameChanged = {
                rename = it
            },
            icon = icon
        )
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
    divider: Boolean = true
) {
    Column {
        Surface(onClick = onClick) {
            Row(
                Modifier
                    .padding(horizontal = PaddingCommon * 2, vertical = PaddingCommon)
                    .then(modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingIcon()
                Spacer(Modifier.width(PaddingCommon * 2))
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                    title()
                }
            }
        }
        if (divider) {
            HorizontalDivider(Modifier.padding(horizontal = PaddingCommon))
        }
    }
}

private val iconByStore by lazy {
    mapOf(
        Motions to Icons.Default.Smartphone,
        Telephonies to Icons.Default.CellTower,
        Traces to Icons.Default.Map
    )
}

private val nameIdByStore by lazy {
    mapOf(
        Motions to R.string.title_motion,
        Telephonies to R.string.title_cells,
        Traces to R.string.title_trace
    )
}