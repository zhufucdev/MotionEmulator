package com.zhufucdev.mock_location_plugin

import android.content.Context
import com.zhufucdev.update.Updater

fun Context.updater() = Updater(BuildConfig.SERVER_URI, BuildConfig.PRODUCT, this)