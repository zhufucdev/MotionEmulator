package com.zhufucdev.motion_emulator.ui.model

import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import com.zhufucdev.motion_emulator.plugin.Plugins
import kotlinx.coroutines.flow.Flow

class PluginViewModel(plugins: List<PluginItem>, val downloadable: Flow<List<PluginItem>>) : ViewModel() {
    val plugins = plugins.toMutableStateList()
    fun save(enabled: List<PluginItem>) {
        Plugins.setPriorities(enabled.map { it.findPlugin()!! })
    }
}