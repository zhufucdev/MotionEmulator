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
    current: String,
    architect: String?
): ReleaseAsset? {
    val req = get {
        url(apiUri)
        parameter("product", product)
        parameter("current", current)
        if (architect != null) {
            parameter("arch", architect)
        }
    }
    if (!req.status.isSuccess()) {
        return null
    }
    return req.body<ReleaseAsset>()
}

suspend fun HttpClient.findAsset(
    apiUri: String,
    vararg category: String,
    current: String? = null,
    architect: String? = null
): List<ProductQuery> {
    val req = get {
        url(apiUri)
        parameter("category", category.joinToString(","))
        if (current != null) {
            parameter("current", current)
        }
        if (architect != null) {
            parameter("arch", architect)
        }
    }
    if (!req.status.isSuccess()) {
        return emptyList()
    }
    return req.body<List<ProductQuery>>()
}