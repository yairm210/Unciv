package com.unciv.logic.multiplayer.apiv2

import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Name of the session cookie returned and expected by the server */
internal const val SESSION_COOKIE_NAME = "id"

/** Default value for max number of players in a lobby if no other value is set */
internal const val DEFAULT_LOBBY_MAX_PLAYERS = 32

/** Default ping frequency for outgoing WebSocket connection in seconds */
internal val DEFAULT_WEBSOCKET_PING_FREQUENCY = 15.seconds

/** Default session timeout expected from multiplayer servers (unreliable) */
internal val DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(15)

/** Default cache expiry timeout to indicate that certain data needs to be re-fetched */
internal val DEFAULT_CACHE_EXPIRY = Duration.ofMinutes(30)

/** Default timeout for a single request (miliseconds) */
internal const val DEFAULT_REQUEST_TIMEOUT = 10_000L

/** Default timeout for connecting to a remote server (miliseconds) */
internal const val DEFAULT_CONNECT_TIMEOUT = 5_000L

/** Default timeout for a single WebSocket PING-PONG roundtrip */
internal val DEFAULT_WEBSOCKET_PING_TIMEOUT = 10.seconds
