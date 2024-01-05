package com.zhufucdev.motion_emulator.extension

import android.content.Context
import com.zhufucdev.motion_emulator.BuildConfig

fun AppUpdater(product: String, context: Context) = com.zhufucdev.update.AppUpdater(
    BuildConfig.server_uri,
    product,
    context,
)

fun AppUpdater(context: Context) = AppUpdater(BuildConfig.product, context)
