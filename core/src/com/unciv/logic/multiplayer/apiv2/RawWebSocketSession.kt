/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors.
 * Based on Ktor's `webSocketRawSession` in buildersCio.kt, Apache License 2.0.
 * Kept engine-independent so the browser target can omit the CIO engine while
 * retaining the same raw-session lifecycle on desktop.
 */
package com.unciv.logic.multiplayer.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/** Opens a raw websocket session without installing Ktor's default control-frame processing. */
internal suspend fun HttpClient.uncivWebSocketRawSession(
    block: HttpRequestBuilder.() -> Unit
): ClientWebSocketSession {
    val request = prepareRequest {
        method = HttpMethod.Get
        url("ws", null, null, null)
        block()
    }

    val result = CompletableDeferred<ClientWebSocketSession>()
    launch {
        try {
            request.body<ClientWebSocketSession, Unit> { session ->
                val sessionCompleted = CompletableDeferred<Unit>()
                result.complete(session)
                session.outgoing.invokeOnClose { cause ->
                    if (cause != null) sessionCompleted.completeExceptionally(cause)
                    else sessionCompleted.complete(Unit)
                }
                sessionCompleted.await()
            }
        } catch (cause: Throwable) {
            result.completeExceptionally(cause)
        }
    }
    return result.await()
}
