package com.unciv.logic.city.managers

import com.unciv.logic.city.City
import com.unciv.models.ruleset.unique.UniqueType.OneTimeTakeOverTilesInCity
import com.unciv.models.ruleset.unique.UniqueType.OneTimeTakeOverTilesInRadius

/**
 *  Source of tile ownership
 *  * Used to index into [CityExpansionTileCounter]
 *  @property All virtual value, readonly, represents sum of all sources, should always equal [City.tiles].size
 *  @property Expansion Tile was acquired via normal expansion via Culture
 *  @property Bought Tile was acquired by buying it with Gold
 *  @property Free Tile was acquired via "take over" triggers [OneTimeTakeOverTilesInCity]/[OneTimeTakeOverTilesInRadius] or dev console
 *  @property Base Tile was initially acquired during city founding
 */
enum class OwnershipSource {
    All,
    Expansion,
    Bought,
    Free,
    Base;

    companion object {
        /** Case-insensitive version of [valueOf] for the console */
        fun parse(text: String): OwnershipSource? =
            entries.firstOrNull { it.name.equals(text, true) }
    }
}
