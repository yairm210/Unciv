package com.unciv.logic.multiplayer

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client helper for [com.unciv.models.metadata.GameParameters.serverAuthoritativeUnitActions]:
 * apply the action on a clone, send it to UncivServer POST /files/{id}/action, commit only on HTTP 200.
 */
object AuthoritativeUnitActions {

    fun isEnabled(gameInfo: GameInfo): Boolean =
        gameInfo.gameParameters.isOnlineMultiplayer
            && gameInfo.gameParameters.serverAuthoritativeUnitActions

    /**
     * Build a zipped save of [gameAfterAction] and POST the action to the configured UncivServer.
     * @return null on success, otherwise an error message
     */
    suspend fun commitAction(
        gameAfterAction: GameInfo,
        type: String,
        unit: MapUnit,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
    ): String? = withContext(Dispatchers.IO) {
        val zipped = UncivFiles.gameInfoToString(gameAfterAction, forceZip = true, updateChecksum = true)
        // Keep JSON field names identical to UncivServer's UnitActionPayload (kotlinx.serialization)
        val body = buildString {
            append("{\"type\":\"").append(type).append("\",")
            append("\"unitId\":").append(unit.id).append(',')
            append("\"fromX\":").append(fromX).append(',')
            append("\"fromY\":").append(fromY).append(',')
            append("\"toX\":").append(toX).append(',')
            append("\"toY\":").append(toY).append(',')
            append("\"newGameData\":\"").append(zipped).append("\"}")
        }
        val server = UncivGame.Current.onlineMultiplayer.multiplayerServer
        server.fileStorage() // ensure auth header is applied for UncivServer
        val url = "${server.getServerUrl().trimEnd('/')}/files/${gameAfterAction.gameId}/action"
        var error: String? = "No response"
        SimpleHttp.sendRequest(
            Net.HttpMethods.POST,
            url,
            body,
            timeout = 30000,
            header = UncivServerFileStorage.authHeader
        ) { success, result, code ->
            error = if (success && code == 200) null
            else "Server rejected action (${code}): $result"
        }
        if (error != null) Log.debug("Authoritative action failed: %s", error)
        error
    }
}
