package com.unciv.models.ruleset.unique

import com.unciv.logic.GameInfo
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.mapregions.Region
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.stats.Stat

data class StateForConditionals(
    val civInfo: Civilization? = null,
    val city: City? = null,
    val unit: MapUnit? = null,
    val tile: Tile? = null,

    val ourCombatant: ICombatant? = null,
    val theirCombatant: ICombatant? = null,
    val attackedTile: Tile? = null,
    val combatAction: CombatAction? = null,

    val region: Region? = null,
    val gameInfo: GameInfo? = civInfo?.gameInfo,

    val ignoreConditionals: Boolean = false,
) {
    constructor(city: City) : this(city.civ, city, tile = city.getCenterTileOrNull())
    constructor(unit: MapUnit) : this(unit.civ, unit = unit, tile = if (unit.hasTile()) unit.getTile() else null)
    constructor(ourCombatant: ICombatant, theirCombatant: ICombatant? = null,
                attackedTile: Tile? = null, combatAction: CombatAction? = null) : this(
        ourCombatant.getCivInfo(),
        (ourCombatant as? CityCombatant)?.city,
        (ourCombatant as? MapUnitCombatant)?.unit,
        ourCombatant.getTile(),
        ourCombatant,
        theirCombatant,
        attackedTile,
        combatAction
    )


    val relevantUnit by lazy {
        if (ourCombatant != null && ourCombatant is MapUnitCombatant) ourCombatant.unit
        else unit
    }

    val relevantTile by lazy { attackedTile
        ?: tile
        // We need to protect against conditionals checking tiles for units pre-placement - see #10425, #10512
        ?: relevantUnit?.run { if (hasTile()) getTile() else null }
        ?: city?.getCenterTileOrNull()
    }

    val relevantCity by lazy {
        if (city != null) return@lazy city
        // Edge case: If we attack a city, the "relevant tile" becomes the attacked tile -
        //  but we DO NOT want that city to become the relevant city because then *our* conditionals get checked against
        //  the *other civ's* cities, leading to e.g. resource amounts being defined as the *other civ's* resource amounts
        val relevantTileForCity = tile ?: relevantUnit?.run { if (hasTile()) getTile() else null }
        val cityForRelevantTile = relevantTileForCity?.getCity()
        if (cityForRelevantTile != null &&
            // ...and we can't use the relevantCiv here either, because that'll cause a loop
            (cityForRelevantTile.civ == civInfo || cityForRelevantTile.civ == relevantUnit?.civ)) return@lazy cityForRelevantTile
        else return@lazy null
    }

    val relevantCiv by lazy {
        civInfo ?:
        relevantCity?.civ ?:
        relevantUnit?.civ
    }

    fun getResourceAmount(resourceName: String): Int {
        return when {
            relevantCity != null -> relevantCity!!.getAvailableResourceAmount(resourceName)
            relevantCiv != null -> relevantCiv!!.getResourceAmount(resourceName)
            else -> 0
        }
    }

    fun getStatAmount(stat: Stat) : Int {
        return when {
            relevantCity != null -> relevantCity!!.getStatReserve(stat)
            relevantCiv != null && stat in Stat.statsWithCivWideField -> relevantCiv!!.getStatReserve(stat)
            else -> 0
        }
    }

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
        val EmptyState = StateForConditionals()
        /** When caching uniques, we need to cache them unmultiplied, and apply multiplication only on retrieval from cache
         * This state lets the multiplication function know that it's always 1:1 */
        val IgnoreMultiplicationForCaching = StateForConditionals(ignoreConditionals = true)
    }

    /**  Used ONLY for stateBasedRandom in [Conditionals.conditionalApplies] to prevent save scumming on [UniqueType.ConditionalChance] */
    override fun hashCode(): Int {
        fun Civilization?.hash() = this?.civName?.hashCode() ?: 0
        fun City?.hash() = this?.id?.hashCode() ?: 0
        fun Tile?.hash() = this?.position?.hashCode() ?: 0
        fun MapUnit?.hash() = if (this == null) 0 else name.hashCode() + (if (hasTile()) 17 * currentTile.hash() else 0)
        fun ICombatant?.hash() = if (this == null) 0
            else if (this is MapUnitCombatant) unit.hash()  // line only serves as `lateinit currentTile not initialized` guard
            else getName().hashCode() + 17 * getTile().hash()
        fun CombatAction?.hash() = this?.name?.hashCode() ?: 0
        fun Region?.hash() = this?.rect?.hashCode() ?: 0

        var result = relevantCiv.hash()
        result = 31 * result + relevantCity.hash()
        result = 31 * result + relevantUnit.hash()
        result = 31 * result + relevantTile.hash()
        result = 31 * result + ourCombatant.hash()
        result = 31 * result + theirCombatant.hash()
        result = 31 * result + attackedTile.hash()
        result = 31 * result + combatAction.hash()
        result = 31 * result + region.hash()
        result = 31 * result + ignoreConditionals.hashCode()
        return result
    }


}

