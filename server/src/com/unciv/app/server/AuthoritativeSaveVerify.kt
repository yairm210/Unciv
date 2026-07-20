package com.unciv.app.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * Minimal save inspection for server-authoritative unit actions.
 * Does not run Unciv game logic — only verifies a proposed save matches the declared unit move/attack.
 */
internal object AuthoritativeSaveVerify {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class UnitActionPayload(
        /** "move" or "attack" */
        val type: String,
        val unitId: Int,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
        /** Full Unciv multiplayer save body (gzip+base64 or raw JSON) after the action */
        val newGameData: String,
    )

    data class UnitPos(val x: Int, val y: Int)

    data class GameMeta(val turns: Int, val currentPlayer: String, val authoritative: Boolean)

    fun decodeToJsonString(raw: String): String {
        val trimmed = raw.trim().replace("\r", "").replace("\n", "")
        if (trimmed.startsWith("{")) return trimmed
        val pad = "=".repeat((4 - trimmed.length % 4) % 4)
        val decoded = Base64.getDecoder().decode(trimmed + pad)
        return GZIPInputStream(ByteArrayInputStream(decoded)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun parseMeta(gameJson: String): GameMeta {
        val root = json.parseToJsonElement(gameJson).jsonObject
        val turns = root["turns"]?.jsonPrimitive?.intOrNull ?: -1
        val currentPlayer = root["currentPlayer"]?.jsonPrimitive?.contentOrNull ?: ""
        val params = root["gameParameters"]?.jsonObject
        val authFlag = when (val el = params?.get("serverAuthoritativeUnitActions")) {
            is JsonPrimitive -> el.booleanOrNull
                ?: el.contentOrNull?.toBooleanStrictOrNull()
                ?: false
            else -> false
        }
        return GameMeta(turns, currentPlayer, authFlag)
    }

    fun findUnitPos(gameJson: String, unitId: Int): UnitPos? {
        val root = json.parseToJsonElement(gameJson).jsonObject
        val tiles = root["tileMap"]?.jsonObject?.get("tileList")?.jsonArray ?: return null
        for (tileEl in tiles) {
            val tile = tileEl.jsonObject
            val pos = tile["position"]?.jsonObject ?: continue
            val x = pos["x"]?.jsonPrimitive?.intOrNull ?: continue
            val y = pos["y"]?.jsonPrimitive?.intOrNull ?: continue
            if (unitOnTile(tile["militaryUnit"], unitId)
                || unitOnTile(tile["civilianUnit"], unitId)
                || unitInAirList(tile["airUnits"], unitId)
            ) return UnitPos(x, y)
        }
        return null
    }

    private fun unitOnTile(el: JsonElement?, unitId: Int): Boolean {
        if (el == null || el is JsonPrimitive) return false
        val obj = el as? JsonObject ?: return false
        return obj["id"]?.jsonPrimitive?.intOrNull == unitId
    }

    private fun unitInAirList(el: JsonElement?, unitId: Int): Boolean {
        val arr = el as? JsonArray ?: return false
        return arr.any { (it as? JsonObject)?.get("id")?.jsonPrimitive?.intOrNull == unitId }
    }

    /**
     * @return null if OK, otherwise error message
     */
    fun verifyAction(oldRaw: String, payload: UnitActionPayload): String? {
        val oldJson = try {
            decodeToJsonString(oldRaw)
        } catch (ex: Exception) {
            return "Cannot decode current save: ${ex.message}"
        }
        val newJson = try {
            decodeToJsonString(payload.newGameData)
        } catch (ex: Exception) {
            return "Cannot decode proposed save: ${ex.message}"
        }

        val oldMeta = parseMeta(oldJson)
        val newMeta = parseMeta(newJson)
        if (!oldMeta.authoritative && !newMeta.authoritative)
            return "Game does not have serverAuthoritativeUnitActions enabled"
        if (oldMeta.turns != newMeta.turns)
            return "Turns changed mid-action (${oldMeta.turns} -> ${newMeta.turns}); use full turn upload"
        if (oldMeta.currentPlayer != newMeta.currentPlayer)
            return "Current player changed mid-action; use full turn upload"

        val oldPos = findUnitPos(oldJson, payload.unitId)
            ?: return "Unit ${payload.unitId} not found in current save"
        if (oldPos.x != payload.fromX || oldPos.y != payload.fromY)
            return "Unit ${payload.unitId} is at (${oldPos.x},${oldPos.y}), not declared from (${payload.fromX},${payload.fromY})"

        val newPos = findUnitPos(newJson, payload.unitId)
        when (payload.type) {
            "move" -> {
                if (newPos == null) return "Unit ${payload.unitId} missing after move"
                if (newPos.x != payload.toX || newPos.y != payload.toY)
                    return "After move unit is at (${newPos.x},${newPos.y}), expected (${payload.toX},${payload.toY})"
            }
            "attack" -> {
                // Attacker may stay or advance; must not remain at an undeclared tile
                if (newPos != null) {
                    val ok = (newPos.x == payload.fromX && newPos.y == payload.fromY)
                        || (newPos.x == payload.toX && newPos.y == payload.toY)
                    if (!ok) return "After attack unit at unexpected (${newPos.x},${newPos.y})"
                }
                // If unit died, allow (newPos == null)
            }
            else -> return "Unknown action type '${payload.type}'"
        }
        return null
    }

    /** True if this PUT is a mid-turn overwrite that should be rejected when authoritative. */
    fun isForbiddenMidTurnPut(oldRaw: String, newRaw: String): Boolean {
        return try {
            val oldMeta = parseMeta(decodeToJsonString(oldRaw))
            if (!oldMeta.authoritative) return false
            val newMeta = parseMeta(decodeToJsonString(newRaw))
            oldMeta.turns == newMeta.turns && oldMeta.currentPlayer == newMeta.currentPlayer
        } catch (_: Exception) {
            false
        }
    }
}
