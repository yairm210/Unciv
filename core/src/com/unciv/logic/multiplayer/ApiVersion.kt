package com.unciv.logic.multiplayer

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
    APIv0, APIv1, APIv2
}
