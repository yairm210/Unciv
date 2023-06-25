package com.unciv.logic.multiplayer

import com.unciv.Constants
import com.unciv.json.json
import com.unciv.logic.multiplayer.ApiVersion.APIv0
import com.unciv.logic.multiplayer.ApiVersion.APIv1
import com.unciv.logic.multiplayer.ApiVersion.APIv2
import com.unciv.logic.multiplayer.apiv2.DEFAULT_CONNECT_TIMEOUT
import com.unciv.logic.multiplayer.apiv2.UncivNetworkException
import com.unciv.logic.multiplayer.apiv2.VersionResponse
import com.unciv.utils.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLParserException
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Enum determining the version of a remote server API implementation
 *
 * [APIv0] is used to reference DropBox. It doesn't support any further features.
 * [APIv1] is used for the UncivServer built-in server implementation as well as
 * for servers implementing this interface. Examples thereof include:
 *  - https://github.com/Mape6/Unciv_server (Python)
 *  - https://gitlab.com/azzurite/unciv-server (NodeJS)
 *  - https://github.com/oynqr/rust_unciv_server (Rust)
 *  - https://github.com/touhidurrr/UncivServer.xyz (NodeJS)
 * This servers may or may not support authentication. The [ServerFeatureSet] may
 * be used to inspect their functionality. [APIv2] is used to reference
 * the heavily extended REST-like HTTP API in combination with a WebSocket
 * functionality for communication. Examples thereof include:
 *   - https://github.com/hopfenspace/runciv
 *
 * A particular server may implement multiple interfaces simultaneously.
 * There's a server version check in the constructor of [OnlineMultiplayer]
 * which handles API auto-detection. The precedence of various APIs is
 * determined by that function:
 * @see [OnlineMultiplayer.determineServerAPI]
 */
enum class ApiVersion {
    APIv0, APIv1, APIv2;

    companion object {
        /**
         * Check the server version by connecting to [baseUrl] without side-effects
         *
         * This function doesn't make use of any currently used workers or high-level
         * connection pools, but instead opens and closes the transports inside it.
         *
         * It will first check if the [baseUrl] equals the [Constants.dropboxMultiplayerServer]
         * to check for [ApiVersion.APIv0]. Dropbox may be unavailable, but this is **not**
         * checked here. It will then try to connect to ``/isalive`` of [baseUrl]. If a
         * HTTP 200 response is received, it will try to decode the response body as JSON.
         * On success (regardless of the content of the JSON), [ApiVersion.APIv1] has been
         * detected. Otherwise, it will try ``/api/version`` to detect [ApiVersion.APIv2]
         * and try to decode its response as JSON. If any of the network calls result in
         * timeout, connection refused or any other networking error, [suppress] is checked.
         * If set, throwing *any* errors is forbidden, so it returns null, otherwise the
         * detected [ApiVersion] is returned or the exception is thrown.
         *
         * Note that the [baseUrl] must include the protocol (either `http://` or `https://`).
         *
         * @throws UncivNetworkException: thrown for any kind of network error
         *   or de-serialization problems (only when [suppress] is false)
         * @throws URLParserException: thrown for invalid [baseUrl] which is
         *   not [Constants.dropboxMultiplayerServer]
         */
        suspend fun detect(baseUrl: String, suppress: Boolean = true, timeout: Long? = null): ApiVersion? {
            if (baseUrl == Constants.dropboxMultiplayerServer) {
                return APIv0
            }
            val fixedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            // This client instance should be used during the API detection
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                    })
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = timeout ?: DEFAULT_CONNECT_TIMEOUT
                }
                defaultRequest {
                    url(fixedBaseUrl)
                }
            }

            // Try to connect to an APIv1 server at first
            val response1 = try {
                client.get("isalive")
            } catch (e: Exception) {
                Log.debug("Failed to fetch '/isalive' at %s: %s", fixedBaseUrl, e.localizedMessage)
                if (!suppress) {
                    client.close()
                    throw UncivNetworkException(e)
                }
                null
            }
            if (response1?.status?.isSuccess() == true) {
                // Some API implementations just return the text "true" on the `isalive` endpoint
                if (response1.bodyAsText().startsWith("true")) {
                    Log.debug("Detected APIv1 at %s (no feature set)", fixedBaseUrl)
                    client.close()
                    return APIv1
                }
                try {
                    val serverFeatureSet: ServerFeatureSet = json().fromJson(ServerFeatureSet::class.java, response1.bodyAsText())
                    Log.debug("Detected APIv1 at %s: %s", fixedBaseUrl, serverFeatureSet)
                    client.close()
                    return APIv1
                } catch (e: Exception) {
                    Log.debug("Failed to de-serialize OK response body of '/isalive' at %s: %s", fixedBaseUrl, e.localizedMessage)
                }
            }

            // Then try to connect to an APIv2 server
            val response2 = try {
                client.get("api/version")
            } catch (e: Exception) {
                Log.debug("Failed to fetch '/api/version' at %s: %s", fixedBaseUrl, e.localizedMessage)
                if (!suppress) {
                    client.close()
                    throw UncivNetworkException(e)
                }
                null
            }
            if (response2?.status?.isSuccess() == true) {
                try {
                    val serverVersion: VersionResponse = response2.body()
                    Log.debug("Detected APIv2 at %s: %s", fixedBaseUrl, serverVersion)
                    client.close()
                    return APIv2
                } catch (e: Exception) {
                    Log.debug("Failed to de-serialize OK response body of '/api/version' at %s: %s", fixedBaseUrl, e.localizedMessage)
                }
            }

            Log.debug("Unable to detect the API version at %s", fixedBaseUrl)
            client.close()
            return null
        }
    }
}
