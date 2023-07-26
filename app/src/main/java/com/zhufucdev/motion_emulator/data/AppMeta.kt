package com.zhufucdev.motion_emulator.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable

/**
 * Utility class to [ApplicationInfo]
 */
data class AppMeta(val name: String?, val icon: Drawable?, val packageName: String) {
    companion object {
        fun of(app: ApplicationInfo, pm: PackageManager) =
            AppMeta(
                (app.labelRes.takeIf { it != 0 }?.let { pm.getText(app.packageName, it, app) })?.toString(),
                try {
                    pm.getApplicationIcon(app)
                } catch (_: Resources.NotFoundException) {
                    null
                },
                app.packageName
            )
    }
}
