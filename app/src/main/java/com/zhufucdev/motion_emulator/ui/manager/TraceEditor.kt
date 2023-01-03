@file:OptIn(ExperimentalMaterial3Api::class)

package com.zhufucdev.motion_emulator.ui.manager

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.*
import com.zhufucdev.motion_emulator.data.MapProjector.toIdeal
import com.zhufucdev.motion_emulator.data.Trace
import com.zhufucdev.motion_emulator.insert
import com.zhufucdev.motion_emulator.toOffset
import com.zhufucdev.motion_emulator.toVector2d
import com.zhufucdev.motion_emulator.ui.CaptionText
import com.zhufucdev.motion_emulator.ui.Swipeable
import com.zhufucdev.motion_emulator.ui.VerticalSpacer
import com.zhufucdev.motion_emulator.ui.theme.*
import kotlinx.coroutines.*
import kotlin.math.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TraceEditor(target: Trace, viewModel: ManagerViewModel<Trace>) {
    var rename by remember { mutableStateOf(target.name) }
    var formulaToken by remember { mutableStateOf(0L) }
    val lifecycleCoroutine = remember { CoroutineScope(Dispatchers.Main) }
    val formulas = remember {
        (target.salt ?: Salt2dData()).elements.map { it.mutable() }.toMutableStateList()
    }
    val factors = remember {
        (target.salt ?: Salt2dData()).factors.map { it.mutable() }.toMutableStateList()
    }

    LaunchedEffect(rename) {
        val captured = rename
        delay(1.seconds)
        if (rename != captured) return@LaunchedEffect

        viewModel.onModify(target.copy(name = rename))
    }

    fun commit() {
        val salt = target.salt ?: Salt2dData()
        viewModel.onModify(
            target.copy(
                name = rename,
                salt =
                if (factors.isEmpty() && formulas.isEmpty())
                    null
                else
                    salt.copy(
                        factors = factors.map { it.immutable() },
                        elements = formulas.map { it.immutable() }
                    )
            )
        )
    }

    LaunchedEffect(formulas) {
        formulaToken = System.currentTimeMillis()
        val captured = formulaToken
        delay(1.seconds)
        if (formulaToken != captured) return@LaunchedEffect

        commit()
    }

    DisposableEffect(Unit) {
        onDispose {
            commit()
            lifecycleCoroutine.cancel()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(paddingCommon),
    ) {
        item {
            TextField(
                label = { Text(stringResource(id = R.string.title_id)) },
                value = target.id,
                onValueChange = {},
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_map_24),
                        contentDescription = null, // only for decoration
                    )
                },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            VerticalSpacer()
        }

        item {
            TextField(
                label = { Text(stringResource(id = R.string.title_name)) },
                value = rename,
                onValueChange = { rename = it },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_edit_24),
                        contentDescription = null
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            VerticalSpacer(paddingCommon * 2)
        }

        item {
            CaptionText(text = stringResource(R.string.caption_salt))
        }

        factors.forEach {
            item(key = it.id, contentType = "factor") {
                Box(Modifier.animateItemPlacement()) {
                    FactorSaltItem(
                        factor = it,
                        onRemove = {
                            val index = factors.indexOf(it)
                            factors.remove(it)

                            lifecycleCoroutine.launch {
                                val result =
                                    viewModel.runtime.snackbarHost.showSnackbar(
                                        message = viewModel.runtime.context.getString(R.string.text_deleted, it.name),
                                        actionLabel = viewModel.runtime.context.getString(R.string.action_undo),
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
                Box(Modifier.animateItemPlacement()) {
                    SaltItemSelector(
                        formula = it,
                        onRemove = {
                            val index = formulas.indexOf(it)
                            formulas.remove(it)

                            lifecycleCoroutine.launch {
                                val result =
                                    viewModel.runtime.snackbarHost.showSnackbar(
                                        message = viewModel.runtime.context.getString(
                                            R.string.text_deleted,
                                            viewModel.runtime.context.getString(saltTypeNames[it.type]!!)
                                        ),
                                        actionLabel = viewModel.runtime.context.getString(R.string.action_undo),
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
            VerticalSpacer(paddingSmall)
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
            Column(Modifier.fillParentMaxWidth().padding(paddingLarge)) {
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                Icon(
                    painter = painterResource(R.drawable.outline_info_24),
                    contentDescription = stringResource(R.string.caption_adding_salt),
                    tint = color
                )
                VerticalSpacer(paddingCommon)
                Text(
                    text = stringResource(R.string.text_salt_common),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
                VerticalSpacer()
                Text(
                    text = stringResource(R.string.text_salt_trace),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color
                )
            }
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
                painter = painterResource(R.drawable.baseline_extension_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        header = {
            Text(
                text = stringResource(R.string.name_random_factor),
                style = MaterialTheme.typography.titleMedium
            )
        },
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
        overview = {
            Text(
                text = factor.name,
                style = MaterialTheme.typography.labelMedium
            )
        },
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
    val boundLeft = max(boundingZero.width, boundingOne.width) + with(LocalDensity.current) { paddingSmall.toPx() }
    val boundBottom = max(boundingZero.height, boundingOne.height) + with(LocalDensity.current) { paddingSmall.toPx() }

    var drawingSize by remember { mutableStateOf(Size.Zero) }
    var controlStart by remember { mutableStateOf(Offset.Zero) }
    var controlEnd by remember { mutableStateOf(Offset.Zero) }
    var projector: Projector by remember { mutableStateOf(BypassProjector) }

    Canvas(
        Modifier.fillMaxWidth().height(240.dp)
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
                            val newProjection = with(projector) { newControl.toVector2d().toIdeal() }
                            if (newProjection.isValid()) {
                                controlStart = newControl
                                factor.distribution.controlStart = newProjection
                            }
                        } else if (isEnd) {
                            val newControl = controlEnd + amountPx
                            val newProjection = with(projector) { newControl.toVector2d().toIdeal() }
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

        drawRect(
            color = outlineColor,
            topLeft = Offset(boundLeft, 0F),
            size = Size(size.width - boundLeft, size.height - boundBottom),
            style = Stroke(width = density)
        )
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
                    modifier = Modifier.padding(actionMargin)
                )
                Text(stringResource(id = R.string.action_add))
            }
        }

        DropdownMenu(
            expanded = menuExpended,
            onDismissRequest = { menuExpended = false },
            offset = pressPoint,
            modifier = Modifier.padding(paddingSmall)
        ) {
            CaptionText(
                text = stringResource(R.string.caption_adding_salt),
                modifier = Modifier.padding(start = paddingCommon, bottom = paddingSmall)
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
        header = {
            Text(
                text = stringResource(R.string.name_rotation),
                style = MaterialTheme.typography.titleMedium
            )
        },
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
                                    .padding(start = paddingSmall)
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
                                    modifier = Modifier.offset(x = paddingCommon)
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
                style = MaterialTheme.typography.labelMedium
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
            Text(
                text = stringResource(R.string.name_custom_matrix),
                style = MaterialTheme.typography.titleMedium
            )
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
                            .padding(end = paddingSmall)
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
                            .padding(end = paddingSmall)
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
    modifier: Modifier = Modifier.fillMaxWidth(),
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
        modifier = modifier
    )
}

@Composable
fun FormulaMenuContent(onClick: (FormulaExpress) -> Unit) {
    CaptionText(
        text = stringResource(R.string.caption_insert_formula),
        modifier = Modifier.padding(start = paddingCommon, bottom = paddingSmall)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun SaltItemScaffold(
    icon: @Composable () -> Unit,
    header: @Composable () -> Unit,
    body: @Composable () -> Unit,
    overview: @Composable () -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var indicatorAnimation by remember { mutableStateOf(Animatable(0F)) }
    var removed by remember { mutableStateOf(false) }
    var targetHeight by remember { mutableStateOf(Int.MAX_VALUE) }
    var animator by remember { mutableStateOf(Animatable(Float.POSITIVE_INFINITY)) }
    val density = LocalDensity.current

    LaunchedEffect(expanded) {
        val targetValue = if (expanded) 180F else 0F
        if (indicatorAnimation.value == targetValue) return@LaunchedEffect

        indicatorAnimation = Animatable(180F - targetValue)
        indicatorAnimation.animateTo(targetValue)
    }

    LaunchedEffect(removed) {
        if (!removed) return@LaunchedEffect

        animator = Animatable(targetHeight / density.density)
        animator.animateTo(0F)
        onRemove()
    }

    Column(
        Modifier.fillMaxWidth()
            .heightIn(max = animator.value.dp)
            .onGloballyPositioned {
                targetHeight = it.size.height
            }
    ) {
        Swipeable(
            foreground = {
                Column(
                    Modifier.fillMaxWidth()
                ) {
                    Surface(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ConstraintLayout(
                            Modifier.padding(
                                start = paddingCommon * 2,
                                end = paddingCommon * 2,
                                top = paddingCommon,
                                bottom = paddingCommon
                            )
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
                            ) {
                                header()
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
                                if (!e) overview()
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
            Text(
                text = header,
                style = MaterialTheme.typography.titleMedium
            )
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
        overview = {
            Text(
                text = stringResource(R.string.text_point, formula[0], formula[1]),
                style = MaterialTheme.typography.labelMedium
            )
        },
        onRemove = onRemove
    )
}

@Composable
@Preview
fun TraceEditorPreview() {
    MotionEmulatorTheme {
        val data by remember {
            mutableStateOf(randomTraceData())
        }
        TraceEditor(
            target = data,
            viewModel = ManagerViewModel.DummyViewModel(Screen.TraceScreen, listOf(data))
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