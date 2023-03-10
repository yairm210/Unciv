package com.unciv.logic.map.tile

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.tile.TileHistory.TileHistoryState.CityCenterType
import java.util.*
import kotlin.collections.HashMap

/**
 * Records events throughout the game related to a tile.
 *
 * Used for end of game replay.
 */
open class TileHistory : IsPartOfGameInfoSerialization {

    /** We use very short identifiers here to save space in the serialization (assuming it isn't zipped) */
    class TileHistoryState(
        /** The name of the civilization owning this tile or `null` if there is no owner. */
        var oc: String? = null,
        /** `null` if this tile does not have a city center. Otherwise this field denotes of which type this city center is. */
        var hc: CityCenterType? = null
    ) : IsPartOfGameInfoSerialization {
        enum class CityCenterType {
            /** A regular city. */
            R,

            /** A capital. */
            C
        }
    }

    /**
     * History records by turn. We use string as the key because that works best for serialization.
     * If we need to look-up values we use the efficient tree map with integer keys below.
     */
    var historyForSerialization = HashMap<String, TileHistoryState>()

    @Transient
    var historyForLookup: TreeMap<Int, TileHistoryState>? = null

    fun recordTakeOwnership(tile: Tile) {
        historyForSerialization[tile.tileMap.gameInfo.turns.toString()] =
                TileHistoryState(
                    tile.getOwner()?.civName,
                    when {
                        tile.isCityCenter() && tile.getCity()
                            ?.isCapital() ?: false -> CityCenterType.C
                        tile.isCityCenter() -> CityCenterType.R
                        else -> null
                    }
                )
        historyForLookup = null
    }

    fun recordRelinquishOwnership(tile: Tile) {
        historyForSerialization[tile.tileMap.gameInfo.turns.toString()] =
                TileHistoryState()
        historyForLookup = null
    }

    fun getState(turn: Int): TileHistoryState {
        historyForLookup = historyForLookup
            ?: TreeMap(historyForSerialization.mapKeys { entry -> entry.key.toInt() })
        return historyForLookup?.floorEntry(turn)?.value ?: TileHistoryState()
    }

    fun clone(): TileHistory {
        val toReturn = TileHistory()
        toReturn.historyForSerialization = HashMap(historyForSerialization)
        return toReturn
    }
}
