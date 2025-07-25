package com.unciv.logic.map.tile

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.tile.TileHistory.TileHistoryState.CityCenterType
import java.util.TreeMap
import org.jetbrains.annotations.VisibleForTesting
import yairm210.purity.annotations.Readonly

/**
 * Records events throughout the game related to a tile.
 *
 * Used for end of game replay.
 *
 * @property history History records by turn.
 * @see com.unciv.ui.screens.victoryscreen.ReplayMap
 */
class TileHistory(
    private val history: TreeMap<Int, TileHistoryState> = TreeMap()
) : IsPartOfGameInfoSerialization, Json.Serializable, Iterable<MutableMap.MutableEntry<Int, TileHistory.TileHistoryState>> {
    class TileHistoryState(
        /** The name of the civilization owning this tile or `null` if there is no owner. */
        val owningCivName: String? = null,
        /** `null` if this tile does not have a city center. Otherwise this field denotes of which type this city center is. */
        val cityCenterType: CityCenterType = CityCenterType.None
    ) : IsPartOfGameInfoSerialization {
        enum class CityCenterType(val serializedRepresentation: String) {
            None("N"),
            Regular("R"),
            Capital("C");

            companion object {
                fun deserialize(s: String): CityCenterType =
                        entries.firstOrNull { it.serializedRepresentation == s } ?: None
            }
        }

        constructor(tile: Tile) : this(
            tile.getOwner()?.civName,
            when {
                !tile.isCityCenter() -> CityCenterType.None
                tile.getCity()?.isCapital() == true -> CityCenterType.Capital
                else -> CityCenterType.Regular
            }
        )
    }

    fun recordTakeOwnership(tile: Tile) {
        history[tile.tileMap.gameInfo.turns] =
                TileHistoryState(tile)
    }

    fun recordRelinquishOwnership(tile: Tile) {
        history[tile.tileMap.gameInfo.turns] =
                TileHistoryState()
    }

    fun getState(turn: Int): TileHistoryState {
        return history.floorEntry(turn)?.value ?: TileHistoryState()
    }

    @Readonly fun clone(): TileHistory = TileHistory(TreeMap(history))

    /** Implement Json.Serializable
     *  - Output looked like this: `history:{0:[Spain,C],12:[China,R]}`
     *    (but now we have turned off simplifed json, so it's properly quoted)
     */
    override fun write(json: Json) {
        for ((key, entry) in history) {
            json.writeArrayStart(key.toString())
            json.writeValue(entry.owningCivName)
            json.writeValue(entry.cityCenterType.serializedRepresentation)
            json.writeArrayEnd()
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        for (entry in jsonData) {
            val turn = entry.name.toInt()
            val owningCivName =
                (if (entry[0].isString) entry.getString(0) else "").takeUnless { it.isEmpty() }
            val cityCenterType = CityCenterType.deserialize(entry.getString(1))
            history[turn] = TileHistoryState(owningCivName, cityCenterType)
        }
    }

    @VisibleForTesting
    fun addTestEntry(turn: Int, entry: TileHistoryState) {
        history[turn] = entry
    }

    @VisibleForTesting
    override fun iterator() = history.iterator()

    // For json serialization, to not serialize an empty object
    override fun equals(other: Any?): Boolean {
        if (other !is TileHistory) return false
        if (history == other.history) return true
        return history.size == other.history.size && history.entries.all { (turn, state) ->
            state == other.history[turn]
        }
    }
}
