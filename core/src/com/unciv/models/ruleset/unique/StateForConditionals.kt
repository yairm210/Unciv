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

    companion object {
        val IgnoreConditionals = StateForConditionals(ignoreConditionals = true)
    }

    /**  Used ONLY for stateBasedRandom in [Conditionals.conditionalApplies] to prevent save scumming on [UniqueType.ConditionalChance] */
    override fun hashCode(): Int {
        fun Civilization?.hash() = this?.civName?.hashCode() ?: 0
        fun City?.hash() = this?.id?.hashCode() ?: 0
        fun Tile?.hash() = this?.position?.hashCode() ?: 0
        fun MapUnit?.hash() = (this?.name?.hashCode() ?: 0) + 17 * this?.currentTile.hash()
        fun ICombatant?.hash() = (this?.getName()?.hashCode() ?: 0) + 17 * this?.getTile().hash()
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
