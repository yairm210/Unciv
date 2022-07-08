package com.unciv.json

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.BackwardCompatibility.readOldFormat
import com.unciv.logic.multiplayer.MultiplayerGameStatus

/**
 * This should save as much bandwidth as possible while (currently) still being able to be read as plaintext.
 *
 * Current format:
 *
 * ```
 * [<gameId>,<turns>,<currentTurnStartTime>,<currentCivName>,<currentPlayerId>]
 * ```
 */
class MultiplayerGameStatusSerializer : Json.Serializer<MultiplayerGameStatus> {
    override fun write(json: Json, status: MultiplayerGameStatus, knownType: Class<*>?) {
        json.writeArrayStart()
        json.writeValue(status.gameId)
        json.writeValue(status.turns)
        json.writeValue(status.currentTurnStartTime)
        json.writeValue(status.currentCivName)
        json.writeValue(status.currentPlayerId)
        json.writeArrayEnd()
    }

    override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): MultiplayerGameStatus {
        val constructor = MultiplayerGameStatus::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val status = constructor.newInstance()

        if (jsonData["civilizations"] != null) {
            readOldFormat(json, jsonData, status)
        } else {
            readNewFormat(json, jsonData, status)
        }

        return status
    }

    private fun readNewFormat(json: Json, jsonData: JsonValue, status: MultiplayerGameStatus) {
        status.gameId = json.readValue(String::class.java, null, "", jsonData[0])
        status.turns = json.readValue(Int::class.java, null, 0, jsonData[1])
        status.currentTurnStartTime = json.readValue(Long::class.java, null, 0L, jsonData[2])
        status.currentCivName =json.readValue(String::class.java, null, "", jsonData[3])
        status.currentPlayerId = json.readValue(String::class.java, null, "", jsonData[4])
    }
}
