package com.unciv.logic.multiplayer.apiv2

import java.time.Duration

/** Name of the session cookie returned and expected by the server */
internal const val SESSION_COOKIE_NAME = "id"

/** Default value for max number of players in a lobby if no other value is set */
internal const val DEFAULT_LOBBY_MAX_PLAYERS = 32

/** Default ping frequency for outgoing WebSocket connection in seconds */
internal const val DEFAULT_WEBSOCKET_PING_FREQUENCY = 15_000L

/** Default session timeout expected from multiplayer servers (unreliable) */
internal val DEFAULT_SESSION_TIMEOUT = Duration.ofSeconds(15 * 60)

/** Default cache expiry timeout to indicate that certain data needs to be re-fetched */
internal val DEFAULT_CACHE_EXPIRY = Duration.ofSeconds(30 * 60)

/** Default timeout for a single request (miliseconds) */
internal const val DEFAULT_REQUEST_TIMEOUT = 10_000L

/** Default timeout for connecting to a remote server (miliseconds) */
internal const val DEFAULT_CONNECT_TIMEOUT = 5_000L

/** Default timeout for a single WebSocket PING-PONG roundtrip */
internal const val DEFAULT_WEBSOCKET_PING_TIMEOUT = 10_000L
