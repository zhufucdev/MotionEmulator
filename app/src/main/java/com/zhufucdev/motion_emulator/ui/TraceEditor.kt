@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.me.stub.*
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.extension.insert
import com.zhufucdev.motion_emulator.extension.toOffset
import com.zhufucdev.motion_emulator.extension.toVector2d
import com.zhufucdev.motion_emulator.ui.component.Appendix
import com.zhufucdev.motion_emulator.ui.component.CaptionText
import com.zhufucdev.motion_emulator.ui.component.Expandable
import com.zhufucdev.motion_emulator.ui.component.Swipeable
import com.zhufucdev.motion_emulator.ui.component.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.component.dragDroppable
import com.zhufucdev.motion_emulator.ui.model.ManagerViewModel
import com.zhufucdev.motion_emulator.ui.theme.*
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TraceEditor(target: Trace, viewModel: ManagerViewModel = viewModel()) {
    var rename by remember { mutableStateOf(target.name) }
    var coordSys by remember { mutableStateOf(target.coordinateSystem) }
    var formulaToken by remember { mutableStateOf(0L) }
    val lifecycleCoroutine = remember { CoroutineScope(Dispatchers.Main) }
    val context = LocalContext.current
    val formulas = remember {
        (target.salt ?: Salt2dData()).elements.map { it.mutable() }.toMutableStateList()
    }
    val factors = remember {
        (target.salt ?: Salt2dData()).factors.map { it.mutable() }.toMutableStateList()
    }
    val listState = rememberLazyListState()

    LaunchedEffect(rename) {
        val captured = rename
        delay(1.seconds)
        if (rename == captured) {
            viewModel.save(target.copy(name = rename))
        }
    }

    suspend fun commit() {
        val salt = target.salt ?: Salt2dData()
        viewModel.save(
            target.copy(
                name = rename,
                salt =
                if (factors.isEmpty() && formulas.isEmpty())
                    null
                else
                    salt.copy(
                        factors = factors.map { it.immutable() },
                        elements = formulas.map { it.immutable() }
                    ),
                coordinateSystem = coordSys
            )
        )
    }

    LaunchedEffect(formulas, factors) {
        formulaToken = System.currentTimeMillis()
        val captured = formulaToken
        delay(1.seconds)
        if (formulaToken != captured) return@LaunchedEffect

        commit()
    }

    DisposableEffect(Unit) {
        onDispose {
            lifecycleCoroutine.launch {
                commit()
            }
            lifecycleCoroutine.cancel()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(PaddingCommon),
        state = listState
    ) {
        basicEditItems(
            id = target.id,
            name = rename,
            onNameChanged = {
                rename = it
                lifecycleCoroutine.launch {
                    viewModel.save(target.copy(name = rename))
                }
            },
            icon = { Icon(painterResource(R.drawable.ic_baseline_map_24), contentDescription = null) },
            bottomMargin = PaddingCommon
        )

        item {
            var expanded by remember { mutableStateOf(false) }
            var textfieldWidth by remember { mutableStateOf(0) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                TextField(
                    value = coordSys.name,
                    label = { Text(stringResource(R.string.name_coord_sys)) },
                    readOnly = true,
                    onValueChange = {},
                    leadingIcon = { Icon(painterResource(R.drawable.ic_baseline_satellite_alt_24), null) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                        .onGloballyPositioned {
                            textfieldWidth = it.size.width
                        }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(
                        with(LocalDensity.current) { textfieldWidth.toDp() }
                    )
                ) {
                    CoordinateSystem.values().forEach {
                        DropdownMenuItem(
                            text = { Text(it.name) },
                            onClick = {
                                expanded = false
                                coordSys = it
                            }
                        )
                    }
                }
            }

            VerticalSpacer(PaddingCommon * 2)
        }

        item {
            CaptionText(text = stringResource(R.string.caption_salt))
        }

        factors.forEach {
            item(key = it.id, contentType = "factor") {
                Box(
                    Modifier
                        .animateItemPlacement()
                        .dragDroppable(
                            element = it,
                            list = factors,
                            state = listState,
                            itemsIgnored = 3
                        )
                ) {
                    FactorSaltItem(
                        factor = it,
                        onRemove = {
                            val index = factors.indexOf(it)
                            factors.remove(it)

                            lifecycleCoroutine.launch {
                                val result =
                                    viewModel.snackbars.showSnackbar(
                                        message = context.getString(R.string.text_deleted, it.name),
                                        actionLabel = context.getString(R.string.action_undo),
                                        withDismissAction = true
                                    )

                                if (result == SnackbarResult.ActionPerformed) {
                                    factors.insert(index, it)
                                }
                            }
                        }
                    )
                }
            }
        }

        formulas.forEach {
            item(key = it.id, contentType = it.type) {
                Box(
                    Modifier
                        .animateItemPlacement()
                        .dragDroppable(
                            element = it,
                            list = formulas,
                            state = listState,
                            itemsIgnored = 3 + factors.size
                        )
                ) {
                    SaltItemSelector(
                        formula = it,
                        onRemove = {
                            val index = formulas.indexOf(it)
                            formulas.remove(it)

                            lifecycleCoroutine.launch {
                                val result =
                                    viewModel.snackbars.showSnackbar(
                                        message = context.getString(
                                            R.string.text_deleted,
                                            context.getString(saltTypeNames[it.type]!!)
                                        ),
                                        actionLabel = context.getString(R.string.action_undo),
                                        withDismissAction = true
                                    )

                                if (result == SnackbarResult.ActionPerformed) {
                                    formulas.insert(index, it)
                                }
                            }
                        }
                    )
                }
            }
        }

        item {
            VerticalSpacer(PaddingSmall)
            NewSaltItem(
                modifier = Modifier.fillMaxWidth(),
                onClick = { isFormula, type ->
                    if (isFormula) {
                        if (type.useMatrix && !formulas.any { f -> f.type == SaltType.Anchor }) {
                            formulas.add(MutableSaltElement(SaltType.Anchor))
                        }
                        formulas.add(MutableSaltElement(type = type))
                    } else {
                        factors.add(MutableFactor(name = nextName(factors)))
                    }
                }
            )
        }

        item {
            VerticalSpacer()
            Appendix(
                { Text(stringResource(R.string.text_salt_common)) },
                { Text(stringResource(R.string.text_salt_trace)) },
                iconDescription = stringResource(R.string.caption_adding_salt),
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(PaddingLarge)
            )
        }
    }
}

private val factorNames = listOf("x", "y", "z", "w", "t", "k")
private fun nextName(existing: List<MutableFactor>): String {
    val candidate = factorNames.firstOrNull { candidate -> !existing.any { it.name == candidate } }
    if (candidate == null) {
        var max = 0
        existing.forEach {
            if (it.name.startsWith("x")) {
                val suffix = it.name.drop(1).toIntOrNull() ?: return@forEach
                if (suffix > max) {
                    max = suffix
                }
            }
        }
        return "x${max + 1}"
    } else {
        return candidate
    }
}

@Composable
fun FactorSaltItem(factor: MutableFactor, onRemove: () -> Unit) {
    var name by remember { mutableStateOf(factor.name) }
    SaltItemScaffold(
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_extension_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        header = { Text(stringResource(R.string.name_random_factor)) },
        body = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        factor.name = it.trim()
                        name = it
                    },
                    label = { Text(stringResource(R.string.title_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                VerticalSpacer()

                FactorCanvas(factor)
            }
        },
        overview = { Text(factor.name) },
        onRemove = onRemove
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun FactorCanvas(factor: MutableFactor) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.bodyMedium
    val outlineColor = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurface
    val controlColor = MaterialTheme.colorScheme.primary
    val referenceColor = MaterialTheme.colorScheme.secondary
    val textZero = remember {
        buildAnnotatedString {
            withStyle(labelStyle.toSpanStyle().copy(color = labelColor)) {
                append('0')
            }
        }
    }
    val textOne = remember {
        buildAnnotatedString {
            withStyle(labelStyle.toSpanStyle().copy(color = labelColor)) {
                append('1')
            }
        }
    }
    val boundingZero = measurer.measure(textZero).getBoundingBox(0)
    val boundingOne = measurer.measure(textOne).getBoundingBox(0)
    val boundLeft = max(boundingZero.width, boundingOne.width) + with(LocalDensity.current) { PaddingSmall.toPx() }
    val boundBottom = max(boundingZero.height, boundingOne.height) + with(LocalDensity.current) { PaddingSmall.toPx() }

    var drawingSize by remember { mutableStateOf(Size.Zero) }
    var controlStart by remember { mutableStateOf(Offset.Zero) }
    var controlEnd by remember { mutableStateOf(Offset.Zero) }
    var projector: Projector by remember { mutableStateOf(BypassProjector) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp)
            .pointerInput(Unit) {
                var isStart = true
                var isEnd = true
                detectDragGestures(
                    onDragStart = { start ->
                        val dts = (start - controlStart).getDistance()
                        val dte = (start - controlEnd).getDistance()
                        isStart = dts < density * 30
                        isEnd = dte < density * 30

                        if (isStart && isEnd) {
                            if (dts < dte) {
                                isStart = true
                                isEnd = false
                            } else {
                                isStart = false
                                isEnd = true
                            }
                        }
                    },
                    onDrag = { change, amountPx ->
                        fun Vector2D.isValid() = x >= 0 && x <= 1 && y >= 0 && y <= 1

                        if (isStart) {
                            val newControl = controlStart + amountPx
                            val newProjection = with(projector) {
                                newControl
                                    .toVector2d()
                                    .toIdeal()
                            }
                            if (newProjection.isValid()) {
                                controlStart = newControl
                                factor.distribution.controlStart = newProjection
                            }
                        } else if (isEnd) {
                            val newControl = controlEnd + amountPx
                            val newProjection = with(projector) {
                                newControl
                                    .toVector2d()
                                    .toIdeal()
                            }
                            if (newProjection.isValid()) {
                                controlEnd = newControl
                                factor.distribution.controlEnd = newProjection
                            }
                        }
                    }
                )
            }
    ) {
        projector = CanvasProjector(boundLeft, boundBottom)
        drawingSize = (projector as CanvasProjector).drawingSize
        if (controlStart == Offset.Zero) {
            controlStart = with(projector) { factor.distribution.controlStart.toTarget() }.toOffset()
            controlEnd = with(projector) { factor.distribution.controlEnd.toTarget() }.toOffset()
        }

        drawText(
            textMeasurer = measurer,
            text = textOne
        )

        drawText(
            textMeasurer = measurer,
            text = textZero,
            topLeft = Offset(x = 0F, y = size.height - boundingZero.height),
        )

        drawText(
            textMeasurer = measurer,
            text = textOne,
            topLeft = Offset(x = size.width - boundingOne.width, y = size.height - boundingOne.height),
        )

        drawPath(
            Path().apply {
                moveTo(boundLeft, size.height - boundBottom)
                cubicTo(
                    controlStart.x,
                    controlStart.y,
                    controlEnd.x,
                    controlEnd.y,
                    drawingSize.width + boundLeft,
                    0F
                )
            },
            color = labelColor,
            style = Stroke(width = density * 2)
        )

        fun drawControlPoint(point: Offset) {
            drawCircle(
                color = controlColor,
                center = point,
                style = Fill,
                radius = density * 5
            )
            drawCircle(
                color = outlineColor,
                center = point,
                style = Stroke(width = density * 2),
                radius = density * 5
            )
        }

        drawRect(
            color = outlineColor,
            topLeft = Offset(boundLeft, 0F),
            size = Size(size.width - boundLeft, size.height - boundBottom),
            style = Stroke(width = density)
        ) // borders

        drawLine(
            color = referenceColor,
            start = controlStart,
            end = Offset(boundLeft, drawingSize.height),
            strokeWidth = density * 2
        )
        drawControlPoint(controlStart)
        drawLine(
            color = referenceColor,
            start = controlEnd,
            end = Offset(drawingSize.width + boundLeft, 0F),
            strokeWidth = density * 2
        )
        drawControlPoint(controlEnd)

    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun NewSaltItem(modifier: Modifier = Modifier, onClick: (Boolean, SaltType) -> Unit) {
    var menuExpended by remember { mutableStateOf(false) }
    var pressPoint by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    Box(modifier.pointerInteropFilter {
        pressPoint = with(density) { DpOffset(it.x.toDp(), 0.dp) }
        false
    }) {
        Surface(
            onClick = { menuExpended = !menuExpended },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_add_24),
                    contentDescription = null,
                    modifier = Modifier.padding(ActionMargin)
                )
                Text(stringResource(id = R.string.action_add))
            }
        }

        DropdownMenu(
            expanded = menuExpended,
            onDismissRequest = { menuExpended = false },
            offset = pressPoint,
            modifier = Modifier.padding(PaddingSmall)
        ) {
            CaptionText(
                text = stringResource(R.string.caption_adding_salt),
                modifier = Modifier.padding(start = PaddingCommon, bottom = PaddingSmall)
            )

            DropdownMenuItem(
                text = { Text(stringResource(R.string.title_random_factor)) },
                onClick = {
                    onClick(false, SaltType.Anchor)
                }
            )

            saltTypeNames.forEach { (t, u) ->
                DropdownMenuItem(
                    text = { Text(stringResource(u)) },
                    onClick = {
                        onClick(true, t)
                        menuExpended = false
                    }
                )
            }
        }
    }
}

@Composable
fun SaltItemSelector(formula: MutableSaltElement, onRemove: () -> Unit) {
    when (formula.type) {
        SaltType.Anchor -> AnchorItem(formula, onRemove)
        SaltType.Translation -> TranslationItem(formula, onRemove)
        SaltType.Rotation -> RotationItem(formula, onRemove)
        SaltType.Scale -> ScaleItem(formula, onRemove)
        SaltType.CustomMatrix -> CustomMatrixItem(formula, onRemove)
    }
}

@Composable
fun AnchorItem(formula: MutableSaltElement, onRemove: () -> Unit) =
    DualFieldSnippet(
        formula = formula,
        icon = painterResource(R.drawable.ic_baseline_anchor_24),
        header = stringResource(R.string.name_anchor),
        labels = arrayOf(
            stringResource(R.string.name_latitude),
            stringResource(R.string.name_longitude)
        ),
        onRemove = onRemove
    )

@Composable
fun TranslationItem(formula: MutableSaltElement, onRemove: () -> Unit) =
    DualFieldSnippet(
        formula = formula,
        icon = painterResource(R.drawable.ic_baseline_open_with_24),
        header = stringResource(R.string.name_translation),
        labels = arrayOf(
            stringResource(R.string.name_x),
            stringResource(R.string.name_y)
        ),
        onRemove = onRemove
    )

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RotationItem(formula: MutableSaltElement, onRemove: () -> Unit) {
    val simpleMode = formula[1] == "0"
    SaltItemScaffold(
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_rotate_left_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        header = { Text(stringResource(R.string.name_rotation)) },
        body = {
            AnimatedContent(simpleMode) { s ->
                if (s) {
                    var value by remember { mutableStateOf(formula.values.first().toFloat()) }
                    var oldValue by remember { mutableStateOf(0F) }
                    var expanded by remember { mutableStateOf(false) }
                    val density = LocalDensity.current
                    var move by remember { mutableStateOf(0F) }
                    var moving by remember { mutableStateOf('S') } // for stop

                    Column {
                        VerticalSpacer()

                        Slider(
                            value = value,
                            onValueChange = {
                                value = it.roundToInt().toFloat()
                                formula.values[0] = value.toString()
                            },
                            valueRange = 0F..360F,
                            steps = 35,
                            modifier = Modifier.weight(1F),
                        )

                        ConstraintLayout(Modifier.fillMaxWidth()) {
                            val (t, b) = createRefs()
                            Text(
                                text = stringResource(
                                    R.string.suffix_deg,
                                    value
                                ).let {
                                    if (moving != 'S') {
                                        stringResource(
                                            if (moving == 'I') R.string.suffix_increasing // for increase
                                            else R.string.suffix_decreasing,
                                            it
                                        )
                                    } else {
                                        it
                                    }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(start = PaddingSmall)
                                    .constrainAs(t) {
                                        top.linkTo(parent.top)
                                        start.linkTo(parent.start)
                                        bottom.linkTo(parent.bottom)
                                    }
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                oldValue = value
                                            },
                                            onDragEnd = {
                                                move = 0F
                                                moving = 'S'
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                move -= dragAmount / density.density
                                                val newValue =
                                                    oldValue + move.sign * E
                                                        .toFloat()
                                                        .pow(abs(move * 0.1F) - 10)
                                                value = if (newValue < 0) {
                                                    0F
                                                } else if (newValue > 360) {
                                                    360F
                                                } else {
                                                    newValue
                                                }
                                                formula.values[0] = value.toString()
                                                moving =
                                                    if (dragAmount < 0) 'I' else 'D' // for decrease
                                            }
                                        )
                                    }
                            )

                            Box(
                                Modifier.constrainAs(b) {
                                    top.linkTo(parent.top)
                                    end.linkTo(parent.end)
                                    bottom.linkTo(parent.bottom)
                                }
                            ) {
                                IconButton(
                                    onClick = { expanded = true },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                    modifier = Modifier.offset(x = PaddingCommon)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_baseline_functions_24),
                                        contentDescription = stringResource(R.string.action_use_input),
                                    )
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_use_input)) },
                                        onClick = {
                                            formula[1] = "1" // turn of simple mode
                                            // don't ask me why
                                            formula[0] = (value * PI / 180).toString()
                                        }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    SaltTextField(
                        label = stringResource(R.string.name_radian),
                        value = formula.values.first(),
                        onValueChange = {
                            formula.values[0] = it
                        }
                    )
                }
            }
        },
        overview = {
            Text(
                text =
                if (simpleMode)
                    stringResource(R.string.suffix_deg, formula.values.first())
                else
                    formula.values.first(),
            )
        },
        onRemove = onRemove
    )
}

@Composable
fun ScaleItem(formula: MutableSaltElement, onRemove: () -> Unit) {
    DualFieldSnippet(
        formula = formula,
        icon = painterResource(R.drawable.ic_baseline_open_in_full_24),
        header = stringResource(R.string.name_scale),
        labels = arrayOf(
            stringResource(R.string.name_ratio_x),
            stringResource(R.string.name_ratio_y)
        ),
        onRemove = onRemove
    )
}

@Composable
fun CustomMatrixItem(formula: MutableSaltElement, onRemove: () -> Unit) {
    SaltItemScaffold(
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_grid_view_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        header = {
            Text(stringResource(R.string.name_custom_matrix))
        },
        body = {
            Column(Modifier.fillMaxWidth()) {
                Row {
                    SaltTextField(
                        label = "",
                        value = formula.values.first(),
                        onValueChange = { formula.values[0] = it },
                        modifier = Modifier
                            .weight(1F)
                            .padding(end = PaddingSmall)
                    )

                    SaltTextField(
                        label = "",
                        value = formula.values[1],
                        onValueChange = { formula.values[1] = it },
                        modifier = Modifier.weight(1F)
                    )
                }

                Row {
                    SaltTextField(
                        label = "",
                        value = formula.values[2],
                        onValueChange = { formula.values[2] = it },
                        modifier = Modifier
                            .weight(1F)
                            .padding(end = PaddingSmall)
                    )

                    SaltTextField(
                        label = "",
                        value = formula.values[3],
                        onValueChange = { formula.values[3] = it },
                        modifier = Modifier.weight(1F)
                    )
                }
            }
        },
        overview = {},
        onRemove = onRemove
    )
}

@Composable
fun SaltTextField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var snipping by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { snipping = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_functions_24),
                    contentDescription = stringResource(R.string.caption_insert_formula)
                )
            }

            DropdownMenu(expanded = snipping, onDismissRequest = { snipping = false }) {
                FormulaMenuContent {
                    if (it.isConstant) {
                        onValueChange(value + it.value)
                    } else {
                        onValueChange(value + it.value + "(")
                    }
                    snipping = false
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier)
    )
}

@Composable
fun FormulaMenuContent(onClick: (FormulaExpress) -> Unit) {
    CaptionText(
        text = stringResource(R.string.caption_insert_formula),
        modifier = Modifier.padding(start = PaddingCommon, bottom = PaddingSmall)
    )
    FormulaExpress.values().forEach {
        DropdownMenuItem(
            text = { Text(stringResource(it.label)) },
            onClick = { onClick(it) }
        )
    }
}

enum class FormulaExpress(val isConstant: Boolean, val value: String, val label: Int) {
    Sin(false, "sin", R.string.name_sin),
    Sinh(false, "sinh", R.string.name_sinh),
    Cos(false, "cos", R.string.name_cos),
    Cosh(false, "cosh", R.string.name_cosh),
    Tan(false, "tan", R.string.name_tan),
    Tanh(false, "tanh", R.string.name_tanh),
    Log(false, "log", R.string.name_log),
    Abs(false, "abs", R.string.name_abs),
    Sqrt(false, "sqrt", R.string.name_sqrt),
    Cbrt(false, "cbrt", R.string.name_cbrt),
    Round(false, "round", R.string.name_round),
    Floor(false, "floor", R.string.name_floor),
    Ceil(false, "ceil", R.string.name_ceil),
    Rand(false, "rand", R.string.name_rand),
    Pi(true, "pi", R.string.name_pi),
    E(true, "e", R.string.name_e)
}

@Composable
fun SaltItemScaffold(
    icon: @Composable () -> Unit,
    header: @Composable BoxScope.() -> Unit,
    body: @Composable BoxScope.() -> Unit,
    overview: @Composable () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var removed by remember { mutableStateOf(false) }
    var targetHeight by remember { mutableStateOf(Int.MAX_VALUE) }
    var animator by remember { mutableStateOf(Animatable(Float.POSITIVE_INFINITY)) }
    val density = LocalDensity.current

    LaunchedEffect(removed) {
        if (!removed) return@LaunchedEffect

        animator = Animatable(targetHeight / density.density)
        animator.animateTo(0F)
        onRemove()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = animator.value.dp)
            .onGloballyPositioned {
                targetHeight = it.size.height
            }
    ) {
        Swipeable(
            foreground = {
                Expandable(
                    icon = icon,
                    header = header,
                    body = body,
                    overview = overview,
                    expanded = expanded,
                    onToggle = { expanded = !expanded }
                )
            },
            backgroundEnd = {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_delete_24),
                    contentDescription = stringResource(
                        id = R.string.action_delete
                    )
                )
            },
            endActivated = { removed = true },
            container = { content ->
                Column(Modifier.fillMaxWidth()) {
                    content()
                }
            },
            fractionWidth = 100.dp,
            fillColor = MaterialTheme.colorScheme.errorContainer
        )

        Divider()
    }
}

@Composable
fun DualFieldSnippet(
    formula: MutableSaltElement,
    icon: Painter,
    header: String,
    labels: Array<String>,
    onRemove: () -> Unit
) {
    SaltItemScaffold(
        icon = {
            Icon(painter = icon, contentDescription = header, modifier = Modifier.size(18.dp))
        },
        header = {
            Text(header)
        },
        body = {
            Column {
                SaltTextField(
                    value = formula[0],
                    onValueChange = { formula[0] = it },
                    label = labels[0]
                )
                VerticalSpacer()
                SaltTextField(
                    value = formula[1],
                    onValueChange = { formula[1] = it },
                    label = labels[1]
                )
            }
        },
        overview = { Text(stringResource(R.string.text_point, formula[0], formula[1])) },
        onRemove = onRemove
    )
}

@Composable
@Preview
fun TraceEditorPreview() {
    val data = randomTraceData()
    MotionEmulatorTheme {
        TraceEditor(
            target = data,
            viewModel = ManagerViewModel(listOf(data), LocalContext.current, listOf(Traces))
        )
    }
}

private fun randomTraceData() = Trace(NanoIdUtils.randomNanoId(), "Near the moon", emptyList())
private val saltTypeNames = mapOf(
    SaltType.Anchor to R.string.name_anchor,
    SaltType.Translation to R.string.name_translation,
    SaltType.Rotation to R.string.name_rotation,
    SaltType.Scale to R.string.name_scale,
    SaltType.CustomMatrix to R.string.title_custom_matrix
)