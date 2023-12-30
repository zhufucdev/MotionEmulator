package com.zhufucdev.mock_location_plugin

import android.content.Context
import com.zhufucdev.update.AppUpdater

fun Context.updater() = AppUpdater(BuildConfig.SERVER_URI, BuildConfig.PRODUCT, this)