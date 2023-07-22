package com.zhufucdev.motion_emulator.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable

/**
 * Represents strategy applied to an app.
 *
 * @param positive in bypass mode, positive means not to hook
 */
data class AppMeta(val name: String?, val icon: Drawable?, val packageName: String, val positive: Boolean) {
    companion object {
        fun of(app: ApplicationInfo, pm: PackageManager, hooked: Boolean) =
            AppMeta(
                (app.labelRes.takeIf { it != 0 }?.let { pm.getText(app.packageName, it, app) })?.toString(),
                try {
                    pm.getApplicationIcon(app)
                } catch (_: Resources.NotFoundException) {
                    null
                },
                app.packageName,
                hooked
            )
    }
}
