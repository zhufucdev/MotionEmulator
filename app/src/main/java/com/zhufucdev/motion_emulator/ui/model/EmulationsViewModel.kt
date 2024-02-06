package com.zhufucdev.motion_emulator.ui.model

import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import com.zhufucdev.motion_emulator.data.DataLoader

class EmulationsViewModel(configs: List<DataLoader<EmulationRef>>) : ViewModel() {
    val configs = configs.toMutableStateList()
}
