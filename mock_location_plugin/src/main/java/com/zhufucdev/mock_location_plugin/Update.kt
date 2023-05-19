package com.zhufucdev.mock_location_plugin

import android.content.Context
import com.zhufucdev.update.Updater
import java.io.File

fun Context.updater() = Updater(BuildConfig.SERVER_URI, BuildConfig.PRODUCT, this, File(externalCacheDir!!, "update"))