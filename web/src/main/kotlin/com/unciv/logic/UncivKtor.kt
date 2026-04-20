@file:Suppress("FunctionOnlyReturningConstant")

package com.unciv.logic

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse

object UncivKtor {
    val client: HttpClient
        get() = throw UncivShowableException("Online networking is disabled on web phase 1")

    suspend fun getOrNull(
        url: String,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse? {
        return null
    }
}
