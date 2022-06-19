package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.BackwardCompatibility.readOldFormat
import com.unciv.logic.multiplayer.Multiplayer

/**
 * This should save as much bandwidth as possible while (currently) still being able to be read as plaintext.
 *
 * Current format:
 *
 * ```
 * [<gameId>,<turns>,<currentTurnStartTime>,<currentCivName>,<currentPlayerId>]
 * ```
 */
class MultiplayerGameStatusSerializer : Json.Serializer<Multiplayer.GameStatus> {
    override fun write(json: Json, status: Multiplayer.GameStatus, knownType: Class<*>?) {
        json.writeArrayStart()
        json.writeValue(status.gameId)
        json.writeValue(status.turns)
        json.writeValue(status.currentTurnStartTime)
        json.writeValue(status.currentCivName)
        json.writeValue(status.currentPlayerId)
        json.writeArrayEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): Multiplayer.GameStatus {
        return if (jsonData["civilizations"] != null) {
            readOldFormat(json, jsonData)
        } else {
            readNewFormat(json, jsonData)
        }
    }

    private fun readNewFormat(json: Json, jsonData: JsonValue): Multiplayer.GameStatus {
        val gameId = json.readValue(String::class.java, null, "", jsonData[0])
        val turns = json.readValue(Int::class.java, null, 0, jsonData[1])
        val currentTurnStartTime = json.readValue(Long::class.java, null, 0L, jsonData[2])
        val currentCivName = json.readValue(String::class.java, null, "", jsonData[3])
        val currentPlayerId = json.readValue(String::class.java, null, "", jsonData[4])
        return Multiplayer.GameStatus(gameId, turns, currentTurnStartTime, currentCivName, currentPlayerId)
    }
}
