package com.zhufucdev.api

import kotlinx.serialization.Serializable

@Serializable
data class ProductQuery(val name: String, val key: String, val category: List<String>) {
    val packageId by lazy {
        category.firstOrNull { Regex("""([a-zA-Z_-]*\.)+[a-zA-Z_-]+""").matches(it) }
    }
}
