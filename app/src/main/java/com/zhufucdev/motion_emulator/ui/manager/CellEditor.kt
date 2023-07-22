package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.stub.CellTimeline
import com.zhufucdev.motion_emulator.effectiveTimeFormat
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun CellEditor(target: CellTimeline, viewModel: EditorViewModel<CellTimeline>) {
    val context = LocalContext.current
    val formatter = remember { context.effectiveTimeFormat() }

    Box(Modifier.padding(paddingCommon)) {
        BasicEdit(
            id = target.id,
            name = target.getDisplayName(formatter),
            onNameChanged = {
                viewModel.onModify(target.copy(name = it))
            },
            icon = { Icon(painterResource(R.drawable.ic_baseline_cell_tower_24), contentDescription = null) }
        )
    }
}