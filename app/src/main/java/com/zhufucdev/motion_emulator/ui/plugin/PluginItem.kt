package com.zhufucdev.motion_emulator.ui.plugin

import com.zhufucdev.api.ProductQuery
import com.zhufucdev.motion_emulator.plugin.Plugin

data class PluginItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val state: PluginItemState
)

enum class PluginItemState {
    NOT_DOWNLOADED, ENABLED, DISABLED, UPDATE
}

fun Plugin.toPluginItem(enabled: Boolean) = PluginItem(
    id = packageName,
    title = name,
    subtitle = description,
    state = if (enabled) PluginItemState.ENABLED else PluginItemState.DISABLED
)

fun ProductQuery.toPluginItem() = PluginItem(
    id = category.firstOrNull { Regex.fromLiteral("""([a-zA-Z_-]*\.)+""").matches(it) } ?: key,
    title = name,
    state = PluginItemState.NOT_DOWNLOADED
)
