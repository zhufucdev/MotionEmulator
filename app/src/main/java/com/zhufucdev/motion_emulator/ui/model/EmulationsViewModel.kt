package com.zhufucdev.motion_emulator.ui.model

import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel

class EmulationsViewModel(configs: List<EmulationRef>) : ViewModel() {
    val configs = configs.toMutableStateList()
}
