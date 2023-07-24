package com.zhufucdev.api

import kotlinx.serialization.Serializable

@Serializable
data class ReleaseAsset(val versionName: String, val productName: String, val url: String)
