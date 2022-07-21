package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.BackwardCompatibility.readOldFormat
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.Multiplayer.GameStatus

/**
 * This should save as much bandwidth as possible while (currently) still being able to be read as plaintext.
 *
 * Current format:
 *
 * ```
 * [<gameId>,<turns>,<currentTurnStartTime>,<currentCivName>,<currentPlayerId>,<serverAddress>]
 * ```
 */
class MultiplayerGameStatusSerializer : Json.Serializer<GameStatus> {
    override fun write(json: Json, status: GameStatus, knownType: Class<*>?) {
        json.writeArrayStart()
        json.writeValue(status.turns)
        json.writeValue(status.currentTurnStartTime)
        json.writeValue(status.currentCivName)
        json.writeValue(status.currentPlayerId)
        json.writeArrayEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): GameStatus {
        return if (jsonData["civilizations"] != null) {
            readOldFormat(json, jsonData)
        } else {
            readNewFormat(json, jsonData)
        }
    }

    private fun readNewFormat(json: Json, jsonData: JsonValue): GameStatus {
        var idx = 0
        val turns = json.readValue(Int::class.java, null, jsonData[idx++])
        val currentTurnStartTime = json.readValue(Long::class.java, null, jsonData[idx++])
        val currentCivName = json.readValue(String::class.java, null, jsonData[idx++])
        val currentPlayerId = json.readValue(String::class.java, null, jsonData[idx++])
        return GameStatus(turns, currentTurnStartTime, currentCivName, currentPlayerId)
    }
}
