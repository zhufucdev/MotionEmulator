package com.zhufucdev.api

import kotlinx.serialization.Serializable

@Serializable
data class ProductQuery(val name: String, val key: String, val category: List<String>)
