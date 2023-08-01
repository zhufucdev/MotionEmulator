package com.zhufucdev.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.isSuccess

suspend fun HttpClient.getReleaseAsset(
    apiUri: String,
    product: String,
    os: String? = null,
    current: String? = null,
    architecture: String? = null
): ReleaseAsset? = runCatching {
    val req = get {
        url {
            url("$apiUri/release")
            parameter("product", product)
            if (os != null) {
                parameter("os", os)
            }
            if (current != null) {
                parameter("current", current)
            }
            if (architecture != null) {
                parameter("arch", architecture)
            }
        }
    }
    if (!req.status.isSuccess()) {
        return@runCatching null
    }
    req.body<ReleaseAsset>()
}.getOrNull()

suspend fun HttpClient.findAsset(
    apiUri: String,
    vararg category: String,
    current: String? = null,
    architect: String? = null
): List<ProductQuery> =
    try {
        val req = get {
            url {
                url("$apiUri/find")
                parameter("category", category.joinToString(","))
            }
            if (current != null) {
                parameter("current", current)
            }
            if (architect != null) {
                parameter("arch", architect)
            }
        }
        if (!req.status.isSuccess()) {
            emptyList()
        } else {
            req.body<List<ProductQuery>>()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }