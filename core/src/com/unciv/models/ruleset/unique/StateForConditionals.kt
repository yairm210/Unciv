package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.ICombatant
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.mapregions.Region
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile

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

    val ignoreConditionals: Boolean = false,
) {
    constructor(city: City) : this(city.civ, city, tile = city.getCenterTile())
    constructor(unit: MapUnit) : this(unit.civ, unit = unit, tile = unit.currentTile)
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
        ?: city?.getCenterTile()
    }

    val relevantCity by lazy {
        city
            ?: relevantTile?.getCity()
    }

    val relevantCiv by lazy {
        civInfo ?:
        relevantCity?.civ ?:
        relevantUnit?.civ
    }

    val gameInfo by lazy { relevantCiv?.gameInfo }

    fun getResourceAmount(resourceName: String): Int {
        if (relevantCity != null) return relevantCity!!.getAvailableResourceAmount(resourceName)
        if (relevantCiv != null) return relevantCiv!!.getResourceAmount(resourceName)
        return 0
    }

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
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

        var result = civInfo.hash()
        result = 31 * result + city.hash()
        result = 31 * result + unit.hash()
        result = 31 * result + tile.hash()
        result = 31 * result + ourCombatant.hash()
        result = 31 * result + theirCombatant.hash()
        result = 31 * result + attackedTile.hash()
        result = 31 * result + combatAction.hash()
        result = 31 * result + region.hash()
        result = 31 * result + ignoreConditionals.hashCode()
        return result
    }


}

