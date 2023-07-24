package com.zhufucdev.motion_emulator.extension

import android.content.Context
import com.zhufucdev.motion_emulator.BuildConfig
import java.io.File

fun Updater(product: String, context: Context) = com.zhufucdev.update.Updater(
    BuildConfig.SERVER_URI,
    product,
    context,
    File(context.externalCacheDir, "update")
)

fun Updater(context: Context) = Updater(BuildConfig.PRODUCT, context)
