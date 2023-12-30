package com.zhufucdev.motion_emulator.plugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Stable
import com.zhufucdev.me.stub.BROADCAST_AUTHORITY

@Stable
class Plugin(val packageName: String, val name: String, val description: String) {

    private fun Context.broadcast(message: String) {
        val target = this@Plugin.packageName
        sendBroadcast(Intent("$BROADCAST_AUTHORITY.$message").apply {
            component = ComponentName(target, "$target.ControllerReceiver")
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        })
    }

    fun notifyStart(context: Context) {
        context.broadcast("EMULATION_START")
    }

    fun notifyStop(context: Context) {
        context.broadcast("EMULATION_STOP")
    }

    fun notifySettingsChanged(context: Context) {
        context.broadcast("SETTINGS_CHANGED")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Plugin

        if (packageName != other.packageName) return false
        if (name != other.name) return false
        return description == other.description
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + description.hashCode()
        return result
    }

}