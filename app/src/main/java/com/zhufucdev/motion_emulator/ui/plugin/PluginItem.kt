package com.zhufucdev.motion_emulator.ui.plugin

import com.zhufucdev.api.ProductQuery
import com.zhufucdev.motion_emulator.plugin.Plugin
import com.zhufucdev.motion_emulator.plugin.Plugins

data class PluginItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    var enabled: Boolean,
    var state: PluginItemState
)

enum class PluginItemState {
    NOT_DOWNLOADED, NONE, UPDATE
}

fun Plugin.toPluginItem(enabled: Boolean) = PluginItem(
    id = packageName,
    title = name,
    subtitle = description,
    enabled = enabled,
    state = PluginItemState.NONE
)

fun ProductQuery.toPluginItem() = PluginItem(
    id = category.firstOrNull { Regex.fromLiteral("""([a-zA-Z_-]*\.)+""").matches(it) } ?: key,
    title = name,
    enabled = false,
    state = PluginItemState.NOT_DOWNLOADED
)

fun PluginItem.findPlugin() = Plugins.available.firstOrNull { it.packageName == id }
