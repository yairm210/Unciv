package com.unciv.logic.map.tile

import com.unciv.logic.IsPartOfGameInfoSerialization
import java.util.*
import kotlin.collections.HashMap

/**
 * Records events throughout the game related to a tile.
 *
 * Used for end of game replay.
 */
open class TileHistory : IsPartOfGameInfoSerialization {

    class TileHistoryState : IsPartOfGameInfoSerialization {
        var ownedByCivName: String? = null
        var isCityCenter: Boolean = false
        var isCapital: Boolean = false

        constructor() {}

        constructor(ownedByCivName: String?, isCityCenter: Boolean, isCapital: Boolean) {
            this.ownedByCivName = ownedByCivName
            this.isCityCenter = isCityCenter
            this.isCapital = isCapital
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
                    tile.isCityCenter(),
                    tile.getCity()?.isCapital() ?: false
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
