package validation

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.teavm.jso.JSBody
import org.teavm.jso.JSObject
import org.teavm.jso.JSExceptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--cancel-repro") {
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            RunnerConsole.errorText(throwable.stackTraceToString())
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler).launch {
            try {
                withTimeout(100) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Unit> { }
                }
                error("cancellable suspension unexpectedly returned")
            } catch (_: TimeoutCancellationException) {
                println("Coroutine cancellation reproduction passed")
            }
        }
        return
    }
    val skipCancellation = args.firstOrNull() == "--skip-cancel"

    val client = HttpClient()
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        RunnerConsole.errorText(throwable.stackTraceToString())
        RunnerConsole.error(JSExceptions.getJSException(throwable.cause ?: throwable))
    }
    CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler).launch {
        try {
            val response = client.get("https://fixture.invalid/status") {
                header("X-Unciv-Probe", "preserved")
            }
            check(response.status == HttpStatusCode(207, "Multi-Status")) { "status was ${response.status}" }
            check(response.headers["X-Fetch-Probe"] == "streamed") { "response header missing" }
            check(response.headers["Content-Length"] == "${"streamed café 🧭 response body".encodeToByteArray().size}") {
                "same-origin Content-Length should be preserved"
            }
            check(response.bodyAsText() == "streamed café 🧭 response body") { "streamed body mismatch" }
            println("Fetch GET response passed")

            // Fetch transparently decompresses response bodies while Content-Length can still
            // describe the compressed wire representation (and Content-Encoding may be hidden by CORS).
            val decodedBody = "x".repeat(1775)
            val decodedResponse = client.get("https://fixture.invalid/compressed-body")
            check(decodedResponse.bodyAsText() == decodedBody) { "decoded response body was truncated or rejected" }
            println("Fetch decoded body with wire Content-Length passed")

            val headResponse = client.head("https://fixture.invalid/head")
            check(headResponse.headers["Content-Length"] == "123") { "HEAD Content-Length was not preserved" }
            check(headResponse.headers["Content-Encoding"] == "x-custom") { "HEAD Content-Encoding was not preserved" }
            check(headResponse.bodyAsText().isEmpty()) { "HEAD response unexpectedly had body bytes" }
            println("Fetch HEAD metadata passed")
            val noContentResponse = client.get("https://fixture.invalid/no-content")
            check(noContentResponse.status == HttpStatusCode.NoContent) { "204 status was ${noContentResponse.status}" }
            check(noContentResponse.headers["Content-Length"] == null) { "204 unexpectedly exposed Content-Length" }
            check(noContentResponse.headers["X-Metadata"] == "kept") { "204 metadata was not preserved" }
            check(noContentResponse.bodyAsText().isEmpty()) { "204 response unexpectedly had body bytes" }
            println("Fetch bodyless status metadata passed")

            val postResponse = client.post("https://fixture.invalid/post") {
                setBody("request café 🧭")
            }
            check(postResponse.status == HttpStatusCode.Created) { "POST status was ${postResponse.status}" }
            check(postResponse.bodyAsText() == "post accepté 🏁") { "POST response body mismatch" }
            println("Fetch POST passed")

            val errorResponse = client.get("https://fixture.invalid/error")
            check(errorResponse.status == HttpStatusCode(503, "Service Unavailable")) { "error status was ${errorResponse.status}" }
            check(errorResponse.bodyAsText() == "server unavailable") { "error body mismatch" }
            println("Fetch HTTP error status passed")

            val networkFailure = try {
                client.get("https://fixture.invalid/network")
                null
            } catch (failure: Throwable) {
                failure
            }
            check(networkFailure != null) { "network rejection unexpectedly returned a response" }
            val failureMessages = generateSequence(networkFailure) { it.cause }.mapNotNull { it.message }.toList()
            check(failureMessages.any { it.contains("Fetch failed for GET https://fixture.invalid/network") }) {
                "network rejection did not preserve the request failure: $failureMessages"
            }
            check(failureMessages.any { it.contains("network offline") }) {
                "network rejection did not preserve the browser failure reason: $failureMessages"
            }

            if (!skipCancellation) {
                try {
                    withTimeout(100) { client.get("https://fixture.invalid/abort") }
                    error("canceled Fetch unexpectedly returned")
                } catch (_: TimeoutCancellationException) {
                    // The JS runner confirms this cancellation reached AbortController.
                }

                try {
                    withTimeout(100) { client.get("https://fixture.invalid/mid-body").bodyAsText() }
                    error("canceled response body unexpectedly completed")
                } catch (_: TimeoutCancellationException) {
                    // The JS runner confirms cancellation reached the Fetch body reader.
                }

                val timeoutClient = HttpClient {
                    install(HttpTimeout) { requestTimeoutMillis = 100 }
                }
                try {
                    timeoutClient.get("https://fixture.invalid/request-timeout")
                    error("request timeout unexpectedly completed")
                } catch (_: HttpRequestTimeoutException) {
                    // The JS runner confirms Ktor request timeout aborted Fetch.
                } finally {
                    timeoutClient.close()
                }
            }
            println("Fetch engine contract passed")
        } catch (t: Throwable) {
            throw t
        } finally {
            client.close()
        }
    }
}

private object RunnerConsole {
    @JvmStatic
    @JSBody(params = ["error"], script = "console.error(error);")
    external fun error(error: JSObject)

    @JvmStatic
    @JSBody(params = ["message"], script = "console.error(message);")
    external fun errorText(message: String)
}
