package com.unciv.logic.multiplayer

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Evgeny's model: client sends a small action JSON; UncivServer applies it to the canonical save
 * and returns the new save. The client must not commit the action locally until HTTP 200.
 */
object AuthoritativeUnitActions {

    fun isEnabled(gameInfo: com.unciv.logic.GameInfo): Boolean =
        gameInfo.gameParameters.isOnlineMultiplayer
            && gameInfo.gameParameters.serverAuthoritativeUnitActions

    /**
     * Ask the server to apply the action. On success returns the new gzipped save body.
     * On failure returns null and [errorOut] is set.
     */
    suspend fun requestAction(
        gameId: String,
        type: String,
        unit: MapUnit,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        errorOut: (String) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val body = buildString {
            append("{\"type\":\"").append(type).append("\",")
            append("\"unitId\":").append(unit.id).append(',')
            append("\"fromX\":").append(fromX.toInt()).append(',')
            append("\"fromY\":").append(fromY.toInt()).append(',')
            append("\"toX\":").append(toX.toInt()).append(',')
            append("\"toY\":").append(toY.toInt()).append('}')
        }
        val server = UncivGame.Current.onlineMultiplayer.multiplayerServer
        val url = "${server.getServerUrl().trimEnd('/')}/files/$gameId/action"
        var newSave: String? = null
        var error: String? = "No response"
        SimpleHttp.sendRequest(
            Net.HttpMethods.POST,
            url,
            body,
            timeout = 60000,
            header = UncivServerFileStorage.authHeader
        ) { success, result, code ->
            if (success && code == 200 && result.isNotBlank()) {
                newSave = result
                error = null
            } else {
                error = "Server rejected action (${code}): $result"
            }
        }
        if (error != null) {
            Log.debug("Authoritative action failed: %s", error)
            errorOut(error!!)
        }
        newSave
    }

    fun loadReturnedSave(zippedSave: String) =
        UncivFiles.gameInfoFromString(zippedSave)
}
